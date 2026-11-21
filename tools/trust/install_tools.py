#!/usr/bin/env python3
import argparse
import hashlib
import io
import json
import platform
import sys
import tarfile
import tempfile
import urllib.request
from datetime import UTC, date, datetime
from pathlib import Path
from typing import Any

from policy import ROOT

TOOLCHAIN_PATH = "config/trust/toolchain.json"
MAX_DOWNLOAD_BYTES = 200_000_000


def load_toolchain(root: Path) -> dict[str, Any]:
    return json.loads((root / TOOLCHAIN_PATH).read_text(encoding="utf-8"))


def platform_key() -> str:
    return f"{platform.system()}-{platform.machine()}"


def resolve(spec: dict[str, Any]) -> tuple[str, str, str | None]:
    if "platforms" not in spec:
        return spec["url"], spec["sha256"], None
    target = spec["platforms"].get(platform_key())
    if target is None:
        raise SystemExit(f"No pinned build for {platform_key()}; add one with its upstream SHA-256.")
    return spec["url"].format(target=target["target"]), target["sha256"], spec["member"].format(target=target["target"])


def download(url: str, digest: str) -> bytes:
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read(MAX_DOWNLOAD_BYTES + 1)
    if len(data) > MAX_DOWNLOAD_BYTES or hashlib.sha256(data).hexdigest() != digest:
        raise SystemExit(f"{url} failed the pinned SHA-256 check.")
    return data


def extract(data: bytes, member: str | None) -> bytes:
    if member is None:
        return data
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as bundle:
        entry = bundle.getmember(member)
        extracted = bundle.extractfile(entry) if entry.isfile() else None
        if extracted is None:
            raise SystemExit(f"Unexpected archive layout: {member} is not a file.")
        return extracted.read()


def write_atomically(target: Path, payload: bytes) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as output:
        output.write(payload)
        temporary = Path(output.name)
    try:
        temporary.chmod(0o755)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def install(name: str, spec: dict[str, Any], install_dir: Path) -> Path:
    url, digest, member = resolve(spec)
    target = install_dir / spec["file"]
    stamp = install_dir / f"{spec['file']}.sha256"
    if target.is_file() and stamp.is_file() and stamp.read_text(encoding="utf-8").strip() == digest:
        return target
    write_atomically(target, extract(download(url, digest), member))
    stamp.write_text(digest + "\n", encoding="utf-8")
    print(f"Installed checksum-verified {name} {spec['version']}.")
    return target


def release_age_violations(toolchain: dict[str, Any], today: date) -> list[str]:
    minimum = toolchain["minimum_release_age_days"]
    return [
        f"{name} {spec['version']} was released {spec['released']}, less than {minimum} days before {today}."
        for name, spec in toolchain["tools"].items()
        if (today - date.fromisoformat(spec["released"])).days < minimum
    ]


def install_all(root: Path) -> dict[str, Path]:
    toolchain = load_toolchain(root)
    install_dir = root / toolchain["install_dir"]
    return {name: install(name, spec, install_dir) for name, spec in toolchain["tools"].items()}


def main() -> int:
    parser = argparse.ArgumentParser(description="Install the checksum-pinned linters used by tools/trust/verify.py.")
    parser.parse_args()
    too_new = release_age_violations(load_toolchain(ROOT), datetime.now(tz=UTC).date())
    for message in too_new:
        print(message)
    for name, path in install_all(ROOT).items():
        print(f"{name}: {path.relative_to(ROOT)}")
    return 1 if too_new else 0


if __name__ == "__main__":
    sys.exit(main())
