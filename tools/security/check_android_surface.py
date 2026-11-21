#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def check_application_flags(app: ET.Element) -> list[str]:
    issues = []
    if app.get(ANDROID + "allowBackup") != "false":
        issues.append("Application backup must stay disabled for private recordings and credentials.")
    if app.get(ANDROID + "usesCleartextTraffic") == "true":
        issues.append("Application-wide cleartext networking is forbidden.")
    if app.get(ANDROID + "debuggable") == "true":
        issues.append("The main manifest must not enable debugging.")
    return issues


def check_component_exports(app: ET.Element) -> list[str]:
    issues = []
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for item in app.findall(kind):
            name = item.get(ANDROID + "name", "<unnamed>")
            exported = item.get(ANDROID + "exported")
            if kind == "activity" and name == ".MainActivity":
                if exported != "true":
                    issues.append("The launch activity must explicitly declare exported=true.")
            elif exported != "false":
                issues.append(f"Review {kind} {name}: non-launch components must explicitly stay unexported.")
    return issues


def check_file_provider(root: Path) -> list[str]:
    paths = ET.parse(root / "app/src/main/res/xml/file_paths.xml").getroot()
    if len(paths) != 1 or paths[0].tag != "cache-path" or paths[0].get("path") != "exports/":
        return ["FileProvider must expose only cache/exports/, never recordings, credentials or filesystem roots."]
    return []


def check_data_extraction(root: Path) -> list[str]:
    issues = []
    extraction = ET.parse(root / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
    for section in ("cloud-backup", "device-transfer"):
        rules = extraction.find(section)
        domains = set() if rules is None else {e.get("domain") for e in rules.findall("exclude") if e.get("path") == "."}
        if not {"root", "file", "database", "sharedpref", "external"}.issubset(domains):
            issues.append(f"{section} must exclude private app storage.")
    return issues


def check(root: Path) -> list[str]:
    manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
    app = manifest.find("application")
    if app is None:
        return ["Manifest has no application."]
    issues = check_application_flags(app)
    issues += check_component_exports(app)
    issues += check_file_provider(root)
    issues += check_data_extraction(root)
    return issues


if __name__ == "__main__":
    findings = check(ROOT)
    for issue in findings:
        print(f"ERROR: {issue}")
    if findings:
        sys.exit(1)
    print("Android backup, component export and FileProvider boundaries passed.")
