#!/usr/bin/env python3
import argparse
import bisect
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLASS_ROOTS = ("app/build/intermediates/built_in_kotlinc", "app/build/intermediates/javac")
COMPILE_TASKS = (
    "compileDebugKotlin",
    "compileDebugJavaWithJavac",
    "compileDebugUnitTestKotlin",
    "compileDebugAndroidTestKotlin",
    "compileReleaseKotlin",
)
JAVAP_BATCH = 200
DEBUG_ONLY_SECTIONS = (
    "LineNumberTable:",
    "LocalVariableTable:",
    "LocalVariableTypeTable:",
    "SourceDebugExtension:",
    "StackMapTable:",
    "Constant pool:",
)
VOLATILE_PREFIXES = ("Classfile ", "Last modified", "MD5 checksum", "SHA-256 checksum")
CONSTANT_REFERENCE = re.compile(r"#\d+(?:[.:]#\d+)?")
SOURCE_MAP = re.compile(r'"SMAP\\n(?:[^"\\]|\\.)*"')
INSTRUCTION = re.compile(r"^(\s+)(\d+): ([a-z][a-z_0-9]*)(.*)$")
SWITCH_CASE = re.compile(r"^(\s+)(-?\d+|default): (\d+)$")
HANDLER_ROW = re.compile(r"^(\s+)(\d+)\s+(\d+)\s+(\d+)\s+(.+)$")
HANDLER_HEADER = re.compile(r"^\s+from\s+to\s+target\s+type$")
BRANCHES = frozenset({"goto", "goto_w", "jsr", "jsr_w"})


def class_files(directory: Path) -> dict[str, Path]:
    found: dict[str, Path] = {}
    for root in CLASS_ROOTS:
        base = directory / root
        if base.is_dir():
            for path in sorted(base.rglob("*.class")):
                found[path.relative_to(base).as_posix()] = path
    return found


def _indent(line: str) -> int:
    return len(line) - len(line.lstrip())


def _without_debug_tables(disassembly: str) -> list[str]:
    kept: list[str] = []
    skip_indent: int | None = None
    for line in disassembly.splitlines():
        stripped = line.strip()
        if skip_indent is not None:
            if stripped and _indent(line) <= skip_indent:
                skip_indent = None
            else:
                continue
        if stripped.startswith(VOLATILE_PREFIXES):
            continue
        if stripped.startswith(DEBUG_ONLY_SECTIONS):
            skip_indent = _indent(line)
            continue
        kept.append(SOURCE_MAP.sub('"SMAP"', CONSTANT_REFERENCE.sub("#", line.rstrip())))
    return kept


class _CodeBlock:
    def __init__(self, lines: list[str]) -> None:
        self.lines = lines
        instructions = [match for match in map(INSTRUCTION.match, lines) if match]
        self.real_offsets = sorted(int(match.group(2)) for match in instructions if match.group(3) != "nop")

    def label(self, offset: int) -> str:
        return f"L{bisect.bisect_left(self.real_offsets, offset)}"

    def instruction(self, match: re.Match[str]) -> str | None:
        _, offset, mnemonic, rest = match.groups()
        if mnemonic == "nop":
            return None
        if mnemonic.startswith("if") or mnemonic in BRANCHES:
            target, _, tail = rest.strip().partition(" ")
            rest = f" {self.label(int(target))} {tail}"
        return f"    {self.label(int(offset))}: {mnemonic}{rest.strip()}"

    def normalized(self) -> list[str]:
        output: list[str] = []
        in_handlers = False
        for line in self.lines:
            instruction = INSTRUCTION.match(line)
            case = SWITCH_CASE.match(line)
            row = HANDLER_ROW.match(line) if in_handlers else None
            if HANDLER_HEADER.match(line):
                in_handlers = True
                output.append(line)
            elif instruction:
                rendered = self.instruction(instruction)
                if rendered is not None:
                    output.append(rendered)
            elif case:
                output.append(f"      {case.group(2)}: {self.label(int(case.group(3)))}")
            elif row:
                labels = " ".join(self.label(int(row.group(index))) for index in (2, 3, 4))
                output.append(f"    {labels} {row.group(5)}")
            else:
                output.append(line)
        return output


def _relabel(lines: list[str]) -> list[str]:
    output: list[str] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        output.append(line)
        index += 1
        if line.strip() != "Code:":
            continue
        start = index
        while index < len(lines) and (not lines[index].strip() or _indent(lines[index]) > _indent(line)):
            index += 1
        output.extend(_CodeBlock(lines[start:index]).normalized())
    return output


def normalize(disassembly: str) -> str:
    return "\n".join(_relabel(_without_debug_tables(disassembly)))


def disassemble(paths: list[Path]) -> dict[Path, str]:
    javap = shutil.which("javap") or str(Path(os.environ.get("JAVA_HOME", "")) / "bin" / "javap")
    result: dict[Path, str] = {}
    for start in range(0, len(paths), JAVAP_BATCH):
        batch = paths[start : start + JAVAP_BATCH]
        output = subprocess.run([javap, "-v", "-p", "-c", *map(str, batch)], check=True, capture_output=True, text=True).stdout
        documents = [chunk for chunk in re.split(r"(?m)^(?=Classfile )", output) if chunk.startswith("Classfile ")]
        if len(documents) != len(batch):
            raise SystemExit("javap returned an unexpected number of classes.")
        for path, document in zip(batch, documents, strict=True):
            result[path] = normalize(document)
    return result


def compare(base_dir: Path, head_dir: Path) -> dict[str, object]:
    base = class_files(base_dir)
    head = class_files(head_dir)
    if not base or not head:
        raise SystemExit("No compiled classes found; compile both trees first.")
    shared = sorted(base.keys() & head.keys())
    base_text = disassemble([base[name] for name in shared])
    head_text = disassemble([head[name] for name in shared])
    changed = [name for name in shared if base_text[base[name]] != head_text[head[name]]]
    return {
        "equivalent": not changed and base.keys() == head.keys(),
        "classes": len(shared),
        "added": sorted(head.keys() - base.keys()),
        "removed": sorted(base.keys() - head.keys()),
        "changed": changed,
    }


def compile_tree(tree: Path) -> None:
    subprocess.run(["./gradlew", "--console=plain", "-q", *COMPILE_TASKS], cwd=tree, check=True)


def build_reference(ref: str, workspace: Path) -> Path:
    tree = workspace / "base"
    subprocess.run(["git", "worktree", "add", "--detach", str(tree), ref], cwd=ROOT, check=True, capture_output=True)
    local_properties = ROOT / "local.properties"
    if local_properties.is_file():
        shutil.copyfile(local_properties, tree / "local.properties")
    compile_tree(tree)
    return tree


def main() -> int:
    parser = argparse.ArgumentParser(description="Prove a change leaves compiled app and test classes unchanged.")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--base-ref")
    source.add_argument("--base-dir", type=Path)
    parser.add_argument("--head-dir", type=Path, default=ROOT)
    parser.add_argument("--skip-head-build", action="store_true")
    parser.add_argument("--report", type=Path, default=ROOT / "build/reports/trust/bytecode.json")
    arguments = parser.parse_args()
    with tempfile.TemporaryDirectory() as temporary:
        workspace = Path(temporary)
        try:
            base_dir = arguments.base_dir or build_reference(arguments.base_ref, workspace)
            if not arguments.skip_head_build:
                compile_tree(arguments.head_dir)
            report = compare(base_dir, arguments.head_dir)
        finally:
            if arguments.base_ref:
                subprocess.run(
                    ["git", "worktree", "remove", "--force", str(workspace / "base")],
                    cwd=ROOT,
                    check=False,
                    capture_output=True,
                )
    arguments.report.parent.mkdir(parents=True, exist_ok=True)
    arguments.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    verdict = "equivalent" if report["equivalent"] else "different"
    print(
        f"Bytecode {verdict}: {report['classes']} shared classes, {len(report['changed'])} changed, "
        f"{len(report['added'])} added, {len(report['removed'])} removed."
    )
    for name in [*report["changed"], *report["added"], *report["removed"]][:20]:
        print(f"  {name}")
    return 0 if report["equivalent"] else 1


if __name__ == "__main__":
    sys.exit(main())
