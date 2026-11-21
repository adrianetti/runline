#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
import time
from collections.abc import Callable
from dataclasses import asdict, dataclass
from pathlib import Path

from install_tools import install_all
from policy import ROOT, git

PYTHON = sys.executable
REPORT_DIR = ROOT / "build/reports/trust"
COVERAGE_REPORT = "app/build/reports/coverage/test/debug/report.xml"
KTLINT_INPUTS = ("app/src/**/*.kt", "*.kts", "app/*.kts")
PYTHON_TEST_DIRS = ("tools/security", "tools/trust")
GRADLE_TASKS = (
    "testDebugUnitTest",
    "createDebugUnitTestCoverageReport",
    "assembleDebug",
    "assembleDebugAndroidTest",
    "assembleRelease",
    "lintDebug",
)
OUTPUT_TAIL_LINES = 40


@dataclass(frozen=True)
class Context:
    base: str | None
    labels: str
    check_labels: bool
    tools: dict[str, Path]


@dataclass(frozen=True)
class Stage:
    name: str
    group: str
    purpose: str
    command: Callable[[Context], list[str]]


@dataclass
class Result:
    name: str
    group: str
    purpose: str
    passed: bool
    seconds: float
    output: str


def with_base(context: Context, command: list[str]) -> list[str]:
    return [*command, "--base", context.base] if context.base else command


def gate_command(context: Context) -> list[str]:
    command = [PYTHON, "tools/trust/check_gates.py", "--labels", context.labels]
    return with_base(context, command) if context.check_labels else command


def python_tests(directory: str) -> Callable[[Context], list[str]]:
    return lambda _: [PYTHON, "-m", "unittest", "discover", "-s", directory, "-t", directory, "-p", "test_*.py"]


STAGES = (
    Stage(
        "toolchain",
        "static",
        "Pinned linters match their SHA-256 and are at least 14 days old",
        lambda _: [PYTHON, "tools/trust/install_tools.py"],
    ),
    Stage(
        "comments",
        "static",
        "Source, scripts, resources and workflows carry no comments",
        lambda _: [PYTHON, "tools/trust/check_comments.py"],
    ),
    Stage(
        "architecture",
        "static",
        "Packages depend only on the layers policy.json allows",
        lambda _: [PYTHON, "tools/trust/check_architecture.py"],
    ),
    Stage(
        "test-integrity",
        "static",
        "Tests stay deterministic and the test count never falls",
        lambda context: with_base(context, [PYTHON, "tools/trust/check_tests.py"]),
    ),
    Stage(
        "gate-integrity",
        "static",
        "Gate files change only with the owner's label; build gates stay on",
        gate_command,
    ),
    Stage(
        "ratchets",
        "static",
        "Baselines, debt and suppressions only shrink; coverage floors only rise",
        lambda context: with_base(context, [PYTHON, "tools/trust/check_ratchets.py"]),
    ),
    Stage(
        "python-lint",
        "static",
        "Ruff lint over every Python tool",
        lambda context: [str(context.tools["ruff"]), "check", "--no-cache", "."],
    ),
    Stage(
        "python-format",
        "static",
        "Ruff formatting over every Python tool",
        lambda context: [str(context.tools["ruff"]), "format", "--no-cache", "--check", "."],
    ),
    Stage(
        "kotlin-format",
        "static",
        "ktlint (ktlint_official) over app sources and build scripts",
        lambda context: ["java", "-jar", str(context.tools["ktlint"]), "--relative", *KTLINT_INPUTS],
    ),
    Stage(
        "kotlin-analysis",
        "static",
        "detekt with every rule; the baseline must match exactly",
        lambda _: [PYTHON, "tools/trust/run_detekt.py"],
    ),
    Stage("trust-evals", "static", "Seeded bad changes prove every trust gate rejects them", python_tests("tools/trust")),
    Stage("security-tests", "static", "Offline tests for the security checkers", python_tests("tools/security")),
    Stage(
        "android-surface", "static", "Backup, export and provider boundaries", lambda _: [PYTHON, "tools/security/check_android_surface.py"]
    ),
    Stage("resources", "static", "English and Spanish resources stay aligned", lambda _: [PYTHON, "tools/check_resources.py"]),
    Stage("icons", "static", "Pinned Tabler icons match their sources", lambda _: [PYTHON, "tools/import_icons.py", "--check"]),
    Stage("routes", "static", "Open route catalog is valid", lambda _: [PYTHON, "tools/check_routes.py"]),
    Stage(
        "gradle",
        "build",
        "Unit tests with coverage, debug, test and release builds, strict Android lint",
        lambda _: ["./gradlew", "--console=plain", *GRADLE_TASKS],
    ),
    Stage(
        "coverage",
        "build",
        "Line coverage per package stays at or above its floor",
        lambda context: with_base(context, [PYTHON, "tools/trust/check_ratchets.py", "--coverage-report", COVERAGE_REPORT]),
    ),
)

FIXERS = (
    [PYTHON, "tools/trust/check_comments.py", "--fix"],
    ["{ruff}", "check", "--no-cache", "--fix", "."],
    ["{ruff}", "format", "--no-cache", "."],
    ["java", "-jar", "{ktlint}", "--relative", "--format", *KTLINT_INPUTS],
)


def default_base() -> str | None:
    for candidate in ("origin/main", "main"):
        result = subprocess.run(["git", "merge-base", "HEAD", candidate], cwd=ROOT, capture_output=True, text=True, check=False)
        if result.returncode == 0:
            return result.stdout.strip()
    return None


def run_stage(stage: Stage, context: Context) -> Result:
    started = time.monotonic()
    completed = subprocess.run(stage.command(context), cwd=ROOT, capture_output=True, text=True, check=False)
    output = "\n".join((completed.stdout + completed.stderr).strip().splitlines()[-OUTPUT_TAIL_LINES:])
    return Result(stage.name, stage.group, stage.purpose, completed.returncode == 0, round(time.monotonic() - started, 1), output)


def markdown(results: list[Result], context: Context) -> str:
    head = git(ROOT, "rev-parse", "HEAD").strip()
    lines = [
        "## Merge trust",
        "",
        f"Commit `{head[:12]}`, base `{(context.base or 'none')[:12]}`.",
        "",
        "| Gate | Result | Seconds | Checks |",
        "| --- | --- | ---: | --- |",
    ]
    lines += [f"| {result.name} | {'pass' if result.passed else 'FAIL'} | {result.seconds} | {result.purpose} |" for result in results]
    for result in results:
        if not result.passed:
            lines += ["", f"### {result.name}", "", "```", result.output, "```"]
    return "\n".join(lines) + "\n"


def write_reports(results: list[Result], context: Context) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    payload = {"base": context.base, "passed": all(result.passed for result in results), "stages": [asdict(r) for r in results]}
    (REPORT_DIR / "verify.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    summary = markdown(results, context)
    (REPORT_DIR / "verify.md").write_text(summary, encoding="utf-8")
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with Path(step_summary).open("a", encoding="utf-8") as handle:
            handle.write(summary)


def fix(tools: dict[str, Path]) -> int:
    for template in FIXERS:
        command = [part.format(ruff=tools["ruff"], ktlint=tools["ktlint"]) for part in template]
        subprocess.run(command, cwd=ROOT, check=False)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Run every merge-trust gate in a fixed order and write a report.")
    scope = parser.add_mutually_exclusive_group()
    scope.add_argument("--static", action="store_true", help="skip the Gradle build stages")
    scope.add_argument("--stage", action="append", choices=[stage.name for stage in STAGES])
    scope.add_argument("--fix", action="store_true", help="apply formatters and comment removal, then stop")
    parser.add_argument("--base", help="git ref the change is measured against (default: merge base with main)")
    parser.add_argument("--labels", default=os.environ.get("TRUST_LABELS", ""))
    parser.add_argument("--no-label-check", action="store_true", help="skip the gate-change label rule (main pushes)")
    arguments = parser.parse_args()
    tools = install_all(ROOT)
    if arguments.fix:
        return fix(tools)
    context = Context(arguments.base or default_base(), arguments.labels, not arguments.no_label_check, tools)
    selected = [
        stage
        for stage in STAGES
        if (arguments.stage and stage.name in arguments.stage)
        or (not arguments.stage and not (arguments.static and stage.group == "build"))
    ]
    results: list[Result] = []
    for stage in selected:
        result = run_stage(stage, context)
        results.append(result)
        print(f"{'pass' if result.passed else 'FAIL'}  {result.name:<16} {result.seconds:>7}s  {result.purpose}", flush=True)
        if not result.passed:
            print("\n".join(f"      {line}" for line in result.output.splitlines()), flush=True)
    write_reports(results, context)
    failed = [result.name for result in results if not result.passed]
    print(f"Merge trust: {len(results) - len(failed)}/{len(results)} gates passed." + (f" Failed: {', '.join(failed)}." if failed else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
