#!/usr/bin/env python3
import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any

import check_architecture
import check_tests
from policy import ROOT, file_at, files_at, load_debt, load_policy, tracked_files

COVERAGE_REPORT = "app/build/reports/coverage/test/debug/report.xml"
COVERAGE_PACKAGE_PREFIX = "com/runline/app/"


def detekt_ids(text: str | None) -> Counter[str]:
    if text is None:
        return Counter()
    root = ET.fromstring(text)
    return Counter(element.text or "" for element in root.iter("ID"))


def lint_issues(text: str | None) -> Counter[str]:
    if text is None:
        return Counter()
    root = ET.fromstring(text)
    keys: Counter[str] = Counter()
    for issue in root.iter("issue"):
        location = issue.find("location")
        file = location.get("file", "") if location is not None else ""
        keys[f"{issue.get('id')}|{file}|{issue.get('message')}"] += 1
    return keys


def debt_entries(text: str | None) -> Counter[str]:
    return Counter(json.loads(text)["entries"]) if text else Counter()


def suppression_counts(texts: dict[str, str], rules: dict[str, Any]) -> Counter[str]:
    counts: Counter[str] = Counter()
    for relative, text in texts.items():
        for rule in rules["patterns"]:
            if relative.endswith(tuple(rule["suffixes"])):
                counts[rule["id"]] += len(re.findall(rule["pattern"], text, flags=re.MULTILINE))
    return counts


def grown(before: Counter[str], after: Counter[str]) -> list[str]:
    return sorted(key for key, count in after.items() if count > before.get(key, 0))


def ledger_failures(root: Path, base: str, gates: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    parsers = {"detekt": detekt_ids, "lint": lint_issues, "debt": debt_entries}
    for name, parse in parsers.items():
        relative = gates["ratcheted_files"][name]
        before_text = file_at(root, base, relative)
        if before_text is None:
            continue
        head_path = root / relative
        after = parse(head_path.read_text(encoding="utf-8") if head_path.is_file() else None)
        failures.extend(f"{relative} gained an entry: {key}" for key in grown(parse(before_text), after))
    return failures


def suppression_failures(root: Path, base: str, rules: dict[str, Any]) -> list[str]:
    suffixes = tuple(suffix for rule in rules["patterns"] for suffix in rule["suffixes"])
    head = {name: (root / name).read_text(encoding="utf-8") for name in tracked_files(root) if name.endswith(suffixes)}
    before = {name: file_at(root, base, name) or "" for name in files_at(root, base) if name.endswith(suffixes)}
    head_counts, base_counts = suppression_counts(head, rules), suppression_counts(before, rules)
    return [
        f"{rule} suppressions rose from {base_counts.get(rule, 0)} to {head_counts[rule]}; fix the finding instead"
        for rule in grown(base_counts, head_counts)
    ]


def stale_debt(root: Path) -> list[str]:
    new_architecture, known_architecture = check_architecture.check(root)
    _, test_violations = check_tests.head_counts(root, load_policy(root).tests)
    current = {violation.key for violation in [*new_architecture, *known_architecture, *test_violations]}
    return [f"config/trust/debt.json lists fixed debt; remove: {key}" for key in sorted(load_debt(root) - current)]


def floor_to_tenth(value: float) -> float:
    return math.floor(value * 10) / 10


def measured_coverage(report: Path) -> dict[str, float]:
    root = ET.parse(report).getroot()

    def line_percent(element: ET.Element) -> float | None:
        for counter in element.findall("counter"):
            if counter.get("type") == "LINE":
                missed, covered = int(counter.get("missed", "0")), int(counter.get("covered", "0"))
                return 100 * covered / (missed + covered) if missed + covered else None
        return None

    measured = {"total": line_percent(root)}
    for package in root.findall("package"):
        name = package.get("name", "").removeprefix(COVERAGE_PACKAGE_PREFIX) or "app"
        measured[name] = line_percent(package)
    return {name: value for name, value in measured.items() if value is not None}


def coverage_failures(root: Path, base: str | None, report: Path | None) -> list[str]:
    relative = load_policy(root).gates["ratcheted_files"]["coverage"]
    floors = json.loads((root / relative).read_text(encoding="utf-8"))["line_floors"]
    failures: list[str] = []
    if base is not None:
        previous_text = file_at(root, base, relative)
        previous = json.loads(previous_text)["line_floors"] if previous_text else {}
        failures.extend(
            f"{relative} lowered or dropped the {name} floor from {value}"
            for name, value in previous.items()
            if floors.get(name, -1) < value
        )
    if report is not None:
        measured = measured_coverage(report)
        for name, floor in sorted(floors.items()):
            actual = measured.get(name)
            if actual is None or actual + 1e-9 < floor:
                failures.append(f"{name} line coverage {actual if actual is None else round(actual, 2)}% is below its floor {floor}%")
            elif floor_to_tenth(actual) > floor:
                print(f"{name} coverage {floor_to_tenth(actual)}% exceeds its floor {floor}%; raise the floor in {relative}.")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description="Debt, baselines, suppressions and coverage may only improve.")
    parser.add_argument("--base")
    parser.add_argument("--coverage-report", type=Path)
    arguments = parser.parse_args()
    policy = load_policy(ROOT)
    failures = stale_debt(ROOT)
    if arguments.base:
        failures += ledger_failures(ROOT, arguments.base, policy.gates)
        failures += suppression_failures(ROOT, arguments.base, policy.suppressions)
    failures += coverage_failures(ROOT, arguments.base, arguments.coverage_report)
    for failure in failures:
        print(failure)
    print(f"Ratchets: {len(failures)} violations.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
