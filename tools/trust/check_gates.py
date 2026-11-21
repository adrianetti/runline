#!/usr/bin/env python3
import argparse
import re
import sys
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Any

from policy import ROOT, git, load_policy


def changed_files(root: Path, base: str) -> list[str]:
    committed = git(root, "diff", "--name-only", "-z", base).split("\0")
    untracked = git(root, "ls-files", "--others", "--exclude-standard", "-z").split("\0")
    return sorted({name for name in [*committed, *untracked] if name})


def protected_changes(changed: list[str], gates: dict[str, Any]) -> list[str]:
    return [name for name in changed if any(fnmatchcase(name, pattern) for pattern in gates["protected_paths"])]


def missing_settings(root: Path, gates: dict[str, Any]) -> list[str]:
    missing: list[str] = []
    for setting in gates["required_settings"]:
        path = root / setting["path"]
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        if not re.search(setting["pattern"], text):
            missing.append(f"{setting['path']} must keep `{setting['pattern']}`")
    return missing


def main() -> int:
    parser = argparse.ArgumentParser(description="Gate files change only with the owner's gate-change label.")
    parser.add_argument("--base")
    parser.add_argument("--labels", default="")
    arguments = parser.parse_args()
    gates = load_policy(ROOT).gates
    failures = missing_settings(ROOT, gates)
    labels = {label.strip() for label in arguments.labels.split(",") if label.strip()}
    if arguments.base:
        touched = protected_changes(changed_files(ROOT, arguments.base), gates)
        if touched and gates["override_label"] not in labels:
            failures.append(f"Protected gate files changed without the '{gates['override_label']}' label: {', '.join(touched)}")
        elif touched:
            print(f"Gate files changed under the '{gates['override_label']}' label: {', '.join(touched)}")
    for failure in failures:
        print(failure)
    print(f"Gates: {len(failures)} violations.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
