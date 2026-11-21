#!/usr/bin/env python3
import json
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
POLICY_PATH = "config/trust/policy.json"
DEBT_PATH = "config/trust/debt.json"


@dataclass(frozen=True)
class Policy:
    comments: dict[str, Any]
    architecture: dict[str, Any]
    tests: dict[str, Any]
    suppressions: dict[str, Any]
    gates: dict[str, Any]


def load_policy(root: Path) -> Policy:
    data = json.loads((root / POLICY_PATH).read_text(encoding="utf-8"))
    return Policy(**{field: data[field] for field in Policy.__dataclass_fields__})


def load_debt(root: Path) -> set[str]:
    path = root / DEBT_PATH
    if not path.is_file():
        return set()
    return set(json.loads(path.read_text(encoding="utf-8"))["entries"])


def git(root: Path, *arguments: str) -> str:
    return subprocess.run(["git", *arguments], cwd=root, check=True, capture_output=True, text=True).stdout


def tracked_files(root: Path) -> list[str]:
    listed = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
    return sorted({name for name in listed.split("\0") if name and (root / name).is_file()})


def file_at(root: Path, ref: str, relative: str) -> str | None:
    result = subprocess.run(["git", "show", f"{ref}:{relative}"], cwd=root, check=False, capture_output=True, text=True)
    return result.stdout if result.returncode == 0 else None


def files_at(root: Path, ref: str) -> list[str]:
    return sorted(name for name in git(root, "ls-tree", "-r", "--name-only", "-z", ref).split("\0") if name)
