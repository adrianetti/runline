#!/usr/bin/env python3
import hashlib
import io
import platform
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

VERSION = "8.30.1"
ARCHIVES = {
    ("Linux", "x86_64"): ("linux_x64", "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb"),
    ("Darwin", "arm64"): ("darwin_arm64", "b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5"),
}
EXPECTED_ARGUMENTS = 2
MAX_ARCHIVE_BYTES = 40_000_000
MAX_BINARY_BYTES = 100_000_000


def main() -> None:
    if len(sys.argv) != EXPECTED_ARGUMENTS:
        raise SystemExit("Usage: install_gitleaks.py OUTPUT_BINARY")
    target = Path(sys.argv[1])
    spec = ARCHIVES.get((platform.system(), platform.machine()))
    if spec is None:
        raise SystemExit("Unsupported platform; use the pinned upstream release and verify its checksum manually.")
    archive, digest = spec
    url = f"https://github.com/gitleaks/gitleaks/releases/download/v{VERSION}/gitleaks_{VERSION}_{archive}.tar.gz"
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read(MAX_ARCHIVE_BYTES + 1)
    if len(data) > MAX_ARCHIVE_BYTES or hashlib.sha256(data).hexdigest() != digest:
        raise SystemExit("Gitleaks archive failed the pinned SHA-256 check.")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as bundle:
        member = bundle.getmember("gitleaks")
        if not member.isfile() or member.size > MAX_BINARY_BYTES:
            raise SystemExit("Unexpected Gitleaks archive layout.")
        binary = bundle.extractfile(member).read()
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as output:
        output.write(binary)
        temporary = Path(output.name)
    try:
        temporary.chmod(0o755)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Installed checksum-verified Gitleaks {VERSION}.")


if __name__ == "__main__":
    main()
