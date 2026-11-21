#!/usr/bin/env python3
import argparse
import re
import sys
from dataclasses import dataclass
from fnmatch import fnmatchcase
from pathlib import Path

from policy import ROOT, Policy, load_policy, tracked_files
from source_text import classify, comment_spans, line_number, strip_comments


@dataclass(frozen=True)
class Finding:
    path: str
    line: int
    text: str


def _allowed_matchers(policy: Policy, relative: str) -> list[re.Pattern[str]]:
    return [re.compile(rule["pattern"]) for rule in policy.comments["allowed"] if fnmatchcase(relative, rule["path"])]


def in_scope(policy: Policy, relative: str) -> bool:
    if Path(relative).suffix not in policy.comments["suffixes"]:
        return False
    return not any(fnmatchcase(relative, pattern) for pattern in policy.comments["exempt_paths"])


def scan(root: Path, policy: Policy, relative: str, *, fix: bool) -> list[Finding]:
    path = root / relative
    text = path.read_text(encoding="utf-8")
    kinds = classify(path, text)
    matchers = _allowed_matchers(policy, relative)

    def allowed(comment: str) -> bool:
        return any(matcher.fullmatch(comment.strip()) for matcher in matchers)

    findings = [
        Finding(relative, line_number(text, span.start), text[span.start : span.end].strip().splitlines()[0][:120])
        for span in comment_spans(kinds)
        if not allowed(text[span.start : span.end])
    ]
    if fix and findings:
        path.write_text(strip_comments(text, kinds, keep=allowed), encoding="utf-8")
    return findings


def check(root: Path, policy: Policy, *, fix: bool = False) -> list[Finding]:
    findings: list[Finding] = []
    for relative in tracked_files(root):
        if in_scope(policy, relative):
            findings.extend(scan(root, policy, relative, fix=fix))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fix", action="store_true")
    arguments = parser.parse_args()
    findings = check(ROOT, load_policy(ROOT), fix=arguments.fix)
    for finding in findings:
        print(f"{finding.path}:{finding.line}: comment not allowed: {finding.text}")
    if arguments.fix:
        print(f"Removed {len(findings)} comments.")
        return 0
    print(f"Comment policy: {len(findings)} violations.")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
