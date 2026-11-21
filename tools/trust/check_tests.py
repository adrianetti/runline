#!/usr/bin/env python3
import argparse
import re
import sys
from dataclasses import dataclass
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Any

from policy import ROOT, file_at, files_at, load_debt, load_policy, tracked_files
from source_text import classify, code_only, line_number

KOTLIN_TEST = re.compile(r"@Test\b")
PYTHON_TEST = re.compile(r"(?m)^\s*def test_\w+\s*\(")


@dataclass(frozen=True)
class Violation:
    key: str
    path: str
    line: int
    message: str


def _under(relative: str, roots: list[str]) -> bool:
    return any(relative.startswith(root.rstrip("/") + "/") for root in roots)


def _is_python_test(relative: str, rules: dict[str, Any]) -> bool:
    return fnmatchcase(relative, rules["python_test_glob"])


def count_tests(relative: str, text: str, rules: dict[str, Any]) -> int:
    if relative.endswith(".kt") and _under(relative, rules["kotlin_test_roots"]):
        return len(KOTLIN_TEST.findall(code_only(text, classify(Path(relative), text))))
    if _is_python_test(relative, rules):
        return len(PYTHON_TEST.findall(text))
    return 0


def forbidden_calls(relative: str, text: str, rules: dict[str, Any]) -> list[Violation]:
    if not relative.endswith(".kt") or not _under(relative, rules["deterministic_roots"]):
        return []
    code = code_only(text, classify(Path(relative), text))
    found: dict[str, Violation] = {}
    for rule in rules["forbidden_calls"]:
        for match in re.finditer(rule["pattern"], code):
            key = f"tests:{rule['id']}:{relative}"
            found.setdefault(key, Violation(key, relative, line_number(code, match.start()), rule["reason"]))
    return list(found.values())


def head_counts(root: Path, rules: dict[str, Any]) -> tuple[int, list[Violation]]:
    total, violations = 0, []
    for relative in tracked_files(root):
        if relative.endswith((".kt", ".py")):
            text = (root / relative).read_text(encoding="utf-8")
            total += count_tests(relative, text, rules)
            violations.extend(forbidden_calls(relative, text, rules))
    return total, violations


def base_count(root: Path, base: str, rules: dict[str, Any]) -> int:
    total = 0
    for relative in files_at(root, base):
        if relative.endswith((".kt", ".py")) and (_under(relative, rules["kotlin_test_roots"]) or _is_python_test(relative, rules)):
            text = file_at(root, base, relative)
            total += count_tests(relative, text or "", rules)
    return total


def main() -> int:
    parser = argparse.ArgumentParser(description="Keep tests deterministic and never let the test count fall.")
    parser.add_argument("--base")
    arguments = parser.parse_args()
    rules = load_policy(ROOT).tests
    debt = load_debt(ROOT)
    total, violations = head_counts(ROOT, rules)
    new = [violation for violation in violations if violation.key not in debt]
    for violation in new:
        print(f"{violation.path}:{violation.line}: {violation.message} [{violation.key}]")
    failed = bool(new)
    message = f"Tests: {total} test cases, {len(new)} determinism violations, {len(violations) - len(new)} recorded as debt."
    if arguments.base:
        before = base_count(ROOT, arguments.base, rules)
        message += f" Base {arguments.base} had {before}."
        if total < before:
            print(f"Test count fell from {before} to {total}; restore or replace the removed tests.")
            failed = True
    print(message)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
