#!/usr/bin/env python3
import argparse
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from install_tools import install_all
from policy import ROOT

INPUTS = "app/src,build.gradle.kts,settings.gradle.kts,app/build.gradle.kts"
CONFIG = "config/detekt/detekt.yml"
BASELINE = "config/detekt/baseline.xml"


def detekt_command(jar: Path, *extra: str) -> list[str]:
    return ["java", "-jar", str(jar), "--input", INPUTS, "--all-rules", "--config", CONFIG, *extra]


def current_findings(jar: Path, workspace: Path) -> set[str]:
    snapshot = workspace / "current.xml"
    subprocess.run(
        detekt_command(jar, "--baseline", str(snapshot), "--create-baseline"),
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    return {element.text or "" for element in ET.parse(snapshot).getroot().iter("ID")}


def committed_findings() -> set[str]:
    return {element.text or "" for element in ET.parse(ROOT / BASELINE).getroot().iter("ID")}


def main() -> int:
    parser = argparse.ArgumentParser(description="Run detekt and require its baseline to match the code exactly.")
    parser.add_argument("--update-baseline", action="store_true")
    arguments = parser.parse_args()
    jar = install_all(ROOT)["detekt"]
    with tempfile.TemporaryDirectory() as temporary:
        current = current_findings(jar, Path(temporary))
        if arguments.update_baseline:
            (ROOT / BASELINE).write_bytes((Path(temporary) / "current.xml").read_bytes())
            print(f"Rewrote {BASELINE} with {len(current)} findings; check_ratchets.py rejects any new entry.")
            return 0
    committed = committed_findings()
    new, fixed = sorted(current - committed), sorted(committed - current)
    if new:
        subprocess.run(detekt_command(jar, "--baseline", BASELINE), cwd=ROOT, check=False)
    for finding in fixed:
        print(f"Fixed finding still in {BASELINE}: {finding}")
    if fixed:
        print("Run python3 tools/trust/run_detekt.py --update-baseline to drop fixed findings.")
    print(f"Detekt: {len(new)} new findings, {len(fixed)} fixed findings left in the baseline, {len(committed)} recorded.")
    return 1 if new or fixed else 0


if __name__ == "__main__":
    sys.exit(main())
