#!/usr/bin/env python3
"""Small app-specific regression guard for backup/export/provider boundaries."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"

def check(root: Path):
    issues = []
    manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
    app = manifest.find("application")
    if app is None:
        return ["Manifest has no application."]
    if app.get(ANDROID + "allowBackup") != "false":
        issues.append("Application backup must stay disabled for private recordings and credentials.")
    if app.get(ANDROID + "usesCleartextTraffic") == "true":
        issues.append("Application-wide cleartext networking is forbidden.")
    if app.get(ANDROID + "debuggable") == "true":
        issues.append("The main manifest must not enable debugging.")
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for item in app.findall(kind):
            name = item.get(ANDROID + "name", "<unnamed>")
            exported = item.get(ANDROID + "exported")
            if kind == "activity" and name == ".MainActivity":
                if exported != "true":
                    issues.append("The launch activity must explicitly declare exported=true.")
            elif exported != "false":
                issues.append(f"Review {kind} {name}: non-launch components must explicitly stay unexported.")
    paths = ET.parse(root / "app/src/main/res/xml/file_paths.xml").getroot()
    if len(paths) != 1 or paths[0].tag != "cache-path" or paths[0].get("path") != "exports/":
        issues.append("FileProvider must expose only cache/exports/, never recordings, credentials or filesystem roots.")
    extraction = ET.parse(root / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
    for section in ("cloud-backup", "device-transfer"):
        rules = extraction.find(section)
        domains = set() if rules is None else {e.get("domain") for e in rules.findall("exclude") if e.get("path") == "."}
        if not {"root", "file", "database", "sharedpref", "external"}.issubset(domains):
            issues.append(f"{section} must exclude private app storage.")
    return issues

if __name__ == "__main__":
    findings = check(ROOT)
    for issue in findings:
        print(f"ERROR: {issue}")
    if findings:
        sys.exit(1)
    print("Android backup, component export and FileProvider boundaries passed.")
