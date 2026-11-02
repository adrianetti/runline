#!/usr/bin/env python3
"""Mutation tests prove security-sensitive manifest regressions fail the guard."""
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

from check_android_surface import ANDROID, ROOT, check

class AndroidSurfaceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for name in ("AndroidManifest.xml", "res/xml/file_paths.xml", "res/xml/data_extraction_rules.xml"):
            relative = Path("app/src/main") / name
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)

    def tearDown(self):
        self.temp.cleanup()

    def edit_manifest(self, change):
        path = self.root / "app/src/main/AndroidManifest.xml"
        tree = ET.parse(path)
        change(tree.getroot().find("application"))
        tree.write(path)

    def test_current_manifest_passes(self):
        self.assertEqual([], check(self.root))

    def test_backup_or_cleartext_or_debug_is_rejected(self):
        for attribute in ("allowBackup", "usesCleartextTraffic", "debuggable"):
            with self.subTest(attribute=attribute):
                self.edit_manifest(lambda app: app.set(ANDROID + attribute, "true"))
                self.assertTrue(check(self.root))
                self.edit_manifest(lambda app: app.set(ANDROID + attribute, "false"))

    def test_exported_service_is_rejected(self):
        self.edit_manifest(lambda app: app.find("service").set(ANDROID + "exported", "true"))
        self.assertTrue(any("service" in message for message in check(self.root)))

    def test_provider_root_is_rejected(self):
        path = self.root / "app/src/main/res/xml/file_paths.xml"
        path.write_text('<paths><root-path name="all" path="/" /></paths>')
        self.assertTrue(any("FileProvider" in message for message in check(self.root)))

    def test_transfer_exclusion_removal_is_rejected(self):
        path = self.root / "app/src/main/res/xml/data_extraction_rules.xml"
        tree = ET.parse(path)
        section = tree.getroot().find("device-transfer")
        section.remove(next(e for e in section if e.get("domain") == "sharedpref"))
        tree.write(path)
        self.assertTrue(any("device-transfer" in message for message in check(self.root)))

if __name__ == "__main__":
    unittest.main()
