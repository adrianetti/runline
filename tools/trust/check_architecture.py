#!/usr/bin/env python3
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from policy import ROOT, load_debt, load_policy, tracked_files
from source_text import classify, code_only, line_number

ROOT_MODULE = "app"


@dataclass(frozen=True)
class Violation:
    key: str
    path: str
    line: int
    message: str


def module_of(relative: str, source_root: str) -> str | None:
    if not relative.startswith(source_root + "/") or not relative.endswith((".kt", ".java")):
        return None
    parts = relative[len(source_root) + 1 :].split("/")
    return ROOT_MODULE if len(parts) == 1 else parts[0]


def referenced_module(segment: str, shared: list[str]) -> str | None:
    if segment in shared:
        return None
    return ROOT_MODULE if segment[:1].isupper() else segment


def policy_cycles(modules: dict[str, list[str]]) -> list[str]:
    cycles: list[str] = []
    state: dict[str, int] = {}

    def visit(name: str, trail: list[str]) -> None:
        state[name] = 1
        for target in modules.get(name, []):
            if state.get(target) == 1:
                cycles.append(" -> ".join([*trail[trail.index(target) :], target]))
            elif target not in state:
                visit(target, [*trail, target])
        state[name] = 2

    for module in sorted(modules):
        if module not in state:
            visit(module, [module])
    return cycles


def _dependency_violations(code: str, relative: str, module: str, rules: dict[str, Any]) -> list[Violation]:
    package = re.escape(rules["package"])
    allowed = set(rules["modules"][module])
    pure = module in rules["pure_modules"]
    violations: list[Violation] = []
    for match in re.finditer(rf"(?<![\w.]){package}\.([A-Za-z_][A-Za-z0-9_]*)", code):
        target = referenced_module(match.group(1), rules["shared_classes"])
        line = line_number(code, match.start())
        if target is None and pure:
            violations.append(
                Violation(
                    f"architecture:{module}->resources:{relative}", relative, line, f"pure module '{module}' must not use Android resources"
                )
            )
        elif target is not None and target != module and target not in allowed:
            violations.append(
                Violation(f"architecture:{module}->{target}:{relative}", relative, line, f"module '{module}' may not depend on '{target}'")
            )
    return violations


def _purity_violations(code: str, relative: str, module: str, rules: dict[str, Any]) -> list[Violation]:
    if module not in rules["pure_modules"]:
        return []
    prefixes = "|".join(re.escape(prefix) for prefix in rules["pure_forbidden_imports"])
    return [
        Violation(
            f"architecture:{module}-impure:{match.group(1)}:{relative}",
            relative,
            line_number(code, match.start()),
            f"pure module '{module}' must not use {match.group(1)}",
        )
        for match in re.finditer(rf"(?<![\w.])({prefixes})", code)
    ]


def scan_file(root: Path, relative: str, module: str, rules: dict[str, Any]) -> list[Violation]:
    if module not in rules["modules"]:
        return [Violation(f"architecture:unknown:{module}", relative, 1, f"module '{module}' is not declared in policy.json")]
    path = root / relative
    text = path.read_text(encoding="utf-8")
    code = code_only(text, classify(path, text))
    return [*_dependency_violations(code, relative, module, rules), *_purity_violations(code, relative, module, rules)]


def check(root: Path) -> tuple[list[Violation], list[Violation]]:
    rules = load_policy(root).architecture
    debt = load_debt(root)
    found: dict[str, Violation] = {}
    for cycle in policy_cycles(rules["modules"]):
        found[f"architecture:cycle:{cycle}"] = Violation(
            f"architecture:cycle:{cycle}", "config/trust/policy.json", 1, f"declared dependency cycle {cycle}"
        )
    for relative in tracked_files(root):
        for source_root in rules["source_roots"]:
            module = module_of(relative, source_root)
            if module is not None:
                for violation in scan_file(root, relative, module, rules):
                    found.setdefault(violation.key, violation)
    new = [violation for key, violation in sorted(found.items()) if key not in debt]
    known = [violation for key, violation in sorted(found.items()) if key in debt]
    return new, known


def main() -> int:
    new, known = check(ROOT)
    for violation in new:
        print(f"{violation.path}:{violation.line}: {violation.message} [{violation.key}]")
    print(f"Architecture: {len(new)} violations, {len(known)} recorded in config/trust/debt.json.")
    return 1 if new else 0


if __name__ == "__main__":
    sys.exit(main())
