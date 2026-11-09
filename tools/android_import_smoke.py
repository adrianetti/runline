#!/usr/bin/env python3
"""Bounded Android Files/Open-with smoke check and native captures; synthetic GPX only.

Run after connectedDebugAndroidTest on an idle API 34 emulator. Never use on a
personal device: this harness changes display settings and adds a synthetic route.
"""
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.runline.app"
OUT = Path(".impeccable/review")
GPX = b"<gpx><trk><trkseg><trkpt lat='0' lon='0'/><trkpt lat='0.01' lon='0'/><trkpt lat='0' lon='0'/></trkseg></trk></gpx>"


def adb(*args, binary=False):
    result = subprocess.check_output(["adb", *map(str, args)], timeout=60)
    return result if binary else result.decode(errors="replace")


def hierarchy():
    adb("shell", "rm", "-f", "/sdcard/import-window.xml")
    result = adb("shell", "uiautomator", "dump", "/sdcard/import-window.xml")
    if "dumped to:" not in result:
        raise RuntimeError("No fresh Android hierarchy")
    return ET.fromstring(adb("shell", "cat", "/sdcard/import-window.xml"))


def wait_for(predicate):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        try:
            root = hierarchy()
            if predicate(root):
                return root
        except (RuntimeError, ET.ParseError):
            pass
        time.sleep(1)
    OUT.mkdir(parents=True, exist_ok=True)
    capture("smoke-failure.png")
    (OUT / "smoke-failure.xml").write_text(adb("shell", "cat", "/sdcard/import-window.xml"))
    raise AssertionError("Android did not reach the expected import state; see the captured screen")


def matches(root, text):
    return [n for n in root.iter("node") if text in (n.get("text"), n.get("content-desc"))]


def tap(text, button=False):
    root = wait_for(lambda r: bool(matches(r, text)))
    nodes = matches(root, text)
    if button:
        nodes = [n for n in nodes if n.get("class") == "android.widget.Button"]
    node = next(n for n in nodes if n.get("enabled") == "true")
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", (left + right) // 2, (top + bottom) // 2)


def capture(name):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    print(f"Captured {name}", flush=True)


def open_fixture(name="track.gpx"):
    adb("shell", "am", "force-stop", PACKAGE)
    result = adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d",
                 f"content://com.runline.app.test.gpx/{name}", "-t", "application/gpx+xml", "-p", PACKAGE)
    assert "Error" not in result, result


def ready(root):
    return any(n.get("class") == "android.widget.EditText" and n.get("enabled") == "true"
               for n in root.iter("node"))


def main():
    # This is a disposable emulator, not a connected physical phone.
    assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1"
    # AGP's connected-test runner removes its APKs at teardown.
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--user", "0", "--locales", "en")
    adb("shell", "cmd", "uimode", "night", "no")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "wm", "user-rotation", "lock", "0")
    open_fixture()
    wait_for(ready)
    capture("phone-import-en.png")
    tap("Cancel", button=True)
    tap("Routes")
    wait_for(lambda r: bool(matches(r, "Follow an open route or add a GPX file.")))
    capture("phone-routes-en.png")

    with tempfile.TemporaryDirectory() as directory:
        file = Path(directory) / "Morning-walk.gpx"
        file.write_bytes(GPX)
        adb("push", file, "/sdcard/Download/Morning-walk.gpx")
    tap("Add route", button=True)
    root = wait_for(lambda r: any(n.get("package") == "com.google.android.documentsui" for n in r.iter("node")))
    if not matches(root, "Downloads"):
        tap("Show roots")
    tap("Downloads")
    wait_for(lambda r: bool(matches(r, "Morning-walk.gpx")))
    capture("phone-file-picker.png")
    tap("Morning-walk.gpx")
    root = wait_for(ready)
    assert matches(root, "Morning-walk"), "Filename was not suggested after the Files result"
    capture("phone-import-files.png")
    tap("Add route", button=True)
    wait_for(lambda r: bool(matches(r, "Morning-walk")) and bool(matches(r, "Out and back")) and not ready(r))
    wait_for(lambda r: not matches(r, "Route imported") and not matches(r, "Loading satellite…"))
    capture("phone-route-added.png")
    # Inspect only our debug app's synthetic fixture, using its debuggable identity.
    files = adb("shell", "run-as", PACKAGE, "ls", "files/routes/imported").split()
    saved = []
    for file in files:
        if file.endswith(".json"):
            metadata = json.loads(adb("shell", "run-as", PACKAGE, "cat", f"files/routes/imported/{file}"))
            if metadata["name"] == "Morning-walk":
                saved.append(metadata)
    assert len(saved) == 1, "Files confirmation must add exactly one route"
    original = adb("exec-out", "run-as", PACKAGE, "cat", f"files/routes/imported/{saved[0]['id']}.gpx", binary=True)
    assert original == GPX, "Files import changed the original GPX bytes"
    print("Files picker → editable suggestion → one saved route with unchanged GPX: passed", flush=True)
    tap("Routes")
    wait_for(lambda r: bool(matches(r, "Morning-walk")) and any("Out and back" in n.get("text", "") for n in r.iter("node")))
    capture("phone-routes-saved.png")

    open_fixture("invalid.gpx")
    root = wait_for(lambda r: bool(matches(r, "Close")))
    assert any(n.get("class") == "android.widget.Button" and n.get("enabled") == "false"
               for n in matches(root, "Add route"))
    capture("phone-import-error.png")

    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--user", "0", "--locales", "es")
    adb("shell", "cmd", "uimode", "night", "yes")
    adb("shell", "settings", "put", "system", "font_scale", "1.3")
    open_fixture()
    wait_for(lambda r: ready(r) and bool(matches(r, "Cancelar")))
    capture("phone-import-es-dark-large.png")
    adb("shell", "wm", "user-rotation", "lock", "1")
    def landscape(root):
        bounds = list(map(int, re.findall(r"\d+", next(root.iter("node")).get("bounds"))))
        return ready(root) and bool(matches(root, "Cancelar")) and bounds[2] > bounds[3]
    root = wait_for(landscape)
    capture("phone-import-es-landscape.png")
    field = next(n for n in root.iter("node") if n.get("class") == "android.widget.EditText")
    left, top, right, bottom = map(int, re.findall(r"\d+", field.get("bounds")))
    adb("shell", "input", "tap", (left + right) // 2, (top + bottom) // 2)
    deadline = time.monotonic() + 30
    while "mInputShown=true" not in adb("shell", "dumpsys", "input_method"):
        assert time.monotonic() < deadline, "The native keyboard did not open"
        time.sleep(1)
    capture("phone-import-es-keyboard.png")


if __name__ == "__main__":
    main()
