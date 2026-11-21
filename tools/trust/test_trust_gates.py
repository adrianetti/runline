#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from datetime import date
from pathlib import Path

import check_architecture
import check_comments
import check_gates
import check_ratchets
import check_tests
from check_bytecode import normalize
from install_tools import release_age_violations
from policy import ROOT, load_policy
from source_text import classify, comment_spans, strip_comments

CORE = "app/src/main/java/com/runline/app/core"
UI = "app/src/main/java/com/runline/app/ui"
TEST = "app/src/test/java/com/runline/app/core"
DETEKT_BASELINE = '<?xml version="1.0" ?>\n<SmellBaseline>\n  <CurrentIssues>\n{ids}  </CurrentIssues>\n</SmellBaseline>\n'
LINT_BASELINE = '<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6">\n{issues}</issues>\n'
LINT_ISSUE = '    <issue id="{id}" message="{message}">\n        <location file="{file}"/>\n    </issue>\n'
BUILD_SCRIPT = """android {
    buildTypes { debug { enableUnitTestCoverage = true } }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
    }
}
"""
JAVAP_BASE = """Classfile /tmp/a/Foo.class
  Last modified 1 Jan 2026; size 100 bytes
  public final int pick(boolean);
    Code:
      stack=1, locals=2, args_size=2
         0: iload_1
         1: ifeq          8
         4: iconst_1
         5: goto          9
         8: iconst_2
         9: ireturn
      LineNumberTable:
        line 3: 0
"""
JAVAP_WITH_NOP = """Classfile /tmp/b/Foo.class
  Last modified 2 Jan 2026; size 101 bytes
  public final int pick(boolean);
    Code:
      stack=1, locals=2, args_size=2
         0: iload_1
         1: ifeq          9
         4: nop
         5: iconst_1
         6: goto          10
         9: iconst_2
        10: ireturn
      LineNumberTable:
        line 3: 0
        line 4: 4
"""


class Repository:
    def __init__(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.git("init", "-q", "-b", "main")
        for relative in ("config/trust/policy.json", "config/trust/coverage.json"):
            self.write(relative, (ROOT / relative).read_text(encoding="utf-8"))
        self.write("config/trust/debt.json", json.dumps({"entries": []}))
        self.write("config/detekt/baseline.xml", DETEKT_BASELINE.format(ids="    <ID>MagicNumber:A.kt$A$3</ID>\n"))
        self.write("app/lint-baseline.xml", LINT_BASELINE.format(issues=LINT_ISSUE.format(id="UseKtx", message="m", file="A.kt")))
        self.write("app/build.gradle.kts", BUILD_SCRIPT)
        self.write(f"{CORE}/Pace.kt", "package com.runline.app.core\n\nfun pace(seconds: Int): Int = seconds / 60\n")
        self.write(f"{UI}/PaceView.kt", "package com.runline.app.ui\n\nimport com.runline.app.core.pace\n\nfun show() = pace(300)\n")
        self.write(
            f"{TEST}/PaceTest.kt",
            "package com.runline.app.core\n\nimport org.junit.Test\n\nclass PaceTest {\n"
            "    @Test fun minutes() = check(pace(120) == 2)\n\n    @Test fun zero() = check(pace(0) == 0)\n}\n",
        )
        self.commit()

    def close(self) -> None:
        self.temporary.cleanup()

    def git(self, *arguments: str) -> str:
        return subprocess.run(
            ["git", "-c", "user.name=eval", "-c", "user.email=eval@example.invalid", *arguments],
            cwd=self.root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout

    def write(self, relative: str, text: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def read(self, relative: str) -> str:
        return (self.root / relative).read_text(encoding="utf-8")

    def commit(self) -> None:
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "base")


class TrustGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repo = Repository()
        self.root = self.repo.root
        self.policy = load_policy(self.root)

    def tearDown(self) -> None:
        self.repo.close()

    def comments(self) -> list[check_comments.Finding]:
        return check_comments.check(self.root, self.policy)

    def test_clean_fixture_passes_every_gate(self) -> None:
        self.assertEqual([], self.comments())
        self.assertEqual(([], []), check_architecture.check(self.root))
        self.assertEqual([], check_tests.head_counts(self.root, self.policy.tests)[1])
        self.assertEqual([], check_ratchets.ledger_failures(self.root, "HEAD", self.policy.gates))
        self.assertEqual([], check_ratchets.suppression_failures(self.root, "HEAD", self.policy.suppressions))
        self.assertEqual([], check_ratchets.stale_debt(self.root))
        self.assertEqual([], check_gates.missing_settings(self.root, self.policy.gates))

    def test_added_comments_are_rejected_in_every_language(self) -> None:
        self.repo.write(
            f"{CORE}/Pace.kt", "package com.runline.app.core\n\n/** Pace. */\nfun pace(seconds: Int): Int = seconds / 60 // per km\n"
        )
        self.repo.write("tools/x.py", "#!/usr/bin/env python3\nVALUE = 1  # why\n")
        self.repo.write("app/src/main/res/values/x.xml", "<resources>\n    <!-- note -->\n</resources>\n")
        self.repo.write(".github/workflows/x.yml", "jobs:\n  # note\n  a: b\n")
        self.assertEqual(
            {f"{CORE}/Pace.kt", "tools/x.py", "app/src/main/res/values/x.xml", ".github/workflows/x.yml"},
            {finding.path for finding in self.comments()},
        )
        self.assertEqual(5, len(self.comments()))

    def test_comment_like_text_inside_literals_is_not_a_comment(self) -> None:
        source = (
            "package com.runline.app.core\n\n"
            "val url = \"https://example.com/a\"\nval quote = '\"'\nfun `it's // fine`() = 1\n"
            'val raw = """/* not */ // nope ${"//" + "x"}"""\nval nested = "${"}"} // still string"\n'
        )
        self.repo.write(f"{CORE}/Strings.kt", source)
        self.repo.write("tools/y.py", 'TEXT = "# not a comment"\n')
        self.repo.write(".github/workflows/y.yml", "    - uses: actions/checkout@0123456789abcdef0123456789abcdef01234567 # v7.0.1\n")
        self.assertEqual([], self.comments())

    def test_fix_removes_comments_and_keeps_code(self) -> None:
        source = "fun a() = 1 // one\n/* outer /* nested */ still */\nfun b() = a()/*x*/+1\n"
        cleaned = strip_comments(source, classify(Path("A.kt"), source))
        self.assertEqual("fun a() = 1\nfun b() = a() +1\n", cleaned)
        self.assertEqual([], comment_spans(classify(Path("A.kt"), cleaned)))

    def test_layer_violations_and_impure_core_are_rejected(self) -> None:
        self.repo.write(
            f"{CORE}/Leak.kt", "package com.runline.app.core\n\nimport android.content.Context\nimport com.runline.app.ui.show\n"
        )
        keys = {violation.key for violation in check_architecture.check(self.root)[0]}
        self.assertIn(f"architecture:core->ui:{CORE}/Leak.kt", keys)
        self.assertIn(f"architecture:core-impure:android.:{CORE}/Leak.kt", keys)

    def test_undeclared_package_is_rejected(self) -> None:
        self.repo.write("app/src/main/java/com/runline/app/widgets/Thing.kt", "package com.runline.app.widgets\n")
        self.assertEqual(["architecture:unknown:widgets"], [v.key for v in check_architecture.check(self.root)[0]])

    def test_recorded_debt_is_known_not_new(self) -> None:
        self.repo.write(f"{CORE}/Leak.kt", "package com.runline.app.core\n\nval x = com.runline.app.ui.show()\n")
        self.repo.write("config/trust/debt.json", json.dumps({"entries": [f"architecture:core->ui:{CORE}/Leak.kt"]}))
        new, known = check_architecture.check(self.root)
        self.assertEqual(([], 1), (new, len(known)))

    def test_declared_layer_cycle_is_rejected(self) -> None:
        modules = {"core": ["ui"], "ui": ["core"]}
        self.assertEqual(["core -> ui -> core"], check_architecture.policy_cycles(modules))

    def test_nondeterministic_tests_are_rejected(self) -> None:
        body = "    @Test fun slow() { Thread.sleep(10); println(java.time.Instant.now()) }\n    @Ignore @Test fun off() = Unit\n"
        self.repo.write(f"{TEST}/SlowTest.kt", f"package com.runline.app.core\n\nclass SlowTest {{\n{body}}}\n")
        self.repo.write(f"{TEST}/TextTest.kt", 'package com.runline.app.core\n\nval text = "Thread.sleep(1) Random()"\n')
        keys = {violation.key for violation in check_tests.head_counts(self.root, self.policy.tests)[1]}
        self.assertEqual({f"tests:{rule}:{TEST}/SlowTest.kt" for rule in ("sleep", "now", "ignored-test")}, keys)

    def test_deleted_test_lowers_the_count(self) -> None:
        self.repo.write(
            f"{TEST}/PaceTest.kt", self.repo.read(f"{TEST}/PaceTest.kt").replace("    @Test fun zero() = check(pace(0) == 0)\n", "")
        )
        self.assertEqual(2, check_tests.base_count(self.root, "HEAD", self.policy.tests))
        self.assertEqual(1, check_tests.head_counts(self.root, self.policy.tests)[0])

    def test_growing_any_ledger_is_rejected(self) -> None:
        self.repo.write(
            "config/detekt/baseline.xml",
            DETEKT_BASELINE.format(ids="    <ID>MagicNumber:A.kt$A$3</ID>\n    <ID>LongMethod:B.kt$B$run</ID>\n"),
        )
        issues = LINT_ISSUE.format(id="UseKtx", message="m", file="A.kt") + LINT_ISSUE.format(id="UseKtx", message="m", file="A.kt")
        self.repo.write("app/lint-baseline.xml", LINT_BASELINE.format(issues=issues))
        self.repo.write("config/trust/debt.json", json.dumps({"entries": ["architecture:core->ui:x"]}))
        failures = check_ratchets.ledger_failures(self.root, "HEAD", self.policy.gates)
        self.assertEqual(3, len(failures))

    def test_shrinking_ledgers_is_allowed(self) -> None:
        self.repo.write("config/detekt/baseline.xml", DETEKT_BASELINE.format(ids=""))
        self.repo.write("app/lint-baseline.xml", LINT_BASELINE.format(issues=""))
        self.assertEqual([], check_ratchets.ledger_failures(self.root, "HEAD", self.policy.gates))

    def test_stale_debt_must_be_removed(self) -> None:
        self.repo.write("config/trust/debt.json", json.dumps({"entries": ["architecture:core->ui:gone.kt"]}))
        self.assertEqual(1, len(check_ratchets.stale_debt(self.root)))

    def test_new_suppressions_are_rejected(self) -> None:
        self.repo.write(f"{CORE}/Pace.kt", '@file:Suppress("MagicNumber")\n' + self.repo.read(f"{CORE}/Pace.kt"))
        self.repo.write("app/src/main/res/layout/a.xml", '<View tools:ignore="HardcodedText"/>\n')
        failures = check_ratchets.suppression_failures(self.root, "HEAD", self.policy.suppressions)
        self.assertEqual(2, len(failures))

    def test_lowered_floor_and_falling_coverage_are_rejected(self) -> None:
        floors = json.loads(self.repo.read("config/trust/coverage.json"))
        floors["line_floors"]["core"] -= 1
        self.repo.write("config/trust/coverage.json", json.dumps(floors))
        report = self.root / "report.xml"
        report.write_text(
            '<report name="app"><package name="com/runline/app/core"><counter type="LINE" missed="50" covered="50"/></package>'
            '<counter type="LINE" missed="50" covered="50"/></report>',
            encoding="utf-8",
        )
        failures = check_ratchets.coverage_failures(self.root, "HEAD", report)
        self.assertTrue(any("lowered" in failure for failure in failures))
        self.assertTrue(any(failure.startswith("core line coverage 50.0%") for failure in failures))

    def test_gate_files_need_the_owner_label(self) -> None:
        self.repo.write("config/trust/policy.json", self.repo.read("config/trust/policy.json").replace('"sleep"', '"nap"'))
        changed = check_gates.changed_files(self.root, "HEAD")
        self.assertEqual(["config/trust/policy.json"], check_gates.protected_changes(changed, self.policy.gates))
        self.assertEqual([], check_gates.protected_changes([f"{CORE}/Pace.kt"], self.policy.gates))

    def test_disabled_build_gate_is_rejected(self) -> None:
        self.repo.write("app/build.gradle.kts", BUILD_SCRIPT.replace("warningsAsErrors = true", "warningsAsErrors = false"))
        self.assertEqual(1, len(check_gates.missing_settings(self.root, self.policy.gates)))

    def test_bytecode_ignores_debug_padding_but_not_logic(self) -> None:
        self.assertEqual(normalize(JAVAP_BASE), normalize(JAVAP_WITH_NOP))
        self.assertNotEqual(normalize(JAVAP_BASE), normalize(JAVAP_WITH_NOP.replace("iconst_2", "iconst_3")))
        self.assertNotEqual(normalize(JAVAP_BASE), normalize(JAVAP_WITH_NOP.replace("ifeq          9", "ifne          9")))

    def test_tools_younger_than_the_cooldown_are_reported(self) -> None:
        toolchain = {"minimum_release_age_days": 14, "tools": {"x": {"version": "1", "released": "2026-11-10"}}}
        self.assertEqual(1, len(release_age_violations(toolchain, date(2026, 11, 20))))
        self.assertEqual([], release_age_violations(toolchain, date(2026, 11, 24)))


if __name__ == "__main__":
    unittest.main()
