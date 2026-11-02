#!/usr/bin/env python3
"""Offline fixtures for fail-closed matching, pagination, and HTTP failures."""
import contextlib
import http.client
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

import check_dependency_advisories as checker


def inventory(count=1):
    return {"schemaVersion": 1, "configurations": [":buildscript:classpath", ":app:releaseRuntimeClasspath"],
            "components": [{"group": "example.test", "name": f"library{i}", "version": "1.2.3-Final",
                            "configurations": [":buildscript:classpath"]} for i in range(count)]}


def vuln(identifier="GHSA-1234-5678-9abc"):
    return {"id": identifier, "modified": "2026-11-02T00:00:00Z"}


class Response(io.BytesIO):
    status = 200


class FakeHttp:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.requests = []
        self.sleeps = []
        self.client = checker.OsvClient(self.open, self.sleeps.append)

    def open(self, request, timeout):
        assert request.full_url == checker.ENDPOINT
        assert timeout == 30
        assert request.get_method() == "POST"
        self.requests.append(json.loads(request.data))
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return Response(response if isinstance(response, bytes) else json.dumps(response).encode())


class DependencyAdvisoriesTest(unittest.TestCase):
    def run_audit(self, fake, data=None):
        components, configurations = checker.validate_inventory(data or inventory())
        return checker.audit(components, configurations, fake.client)

    def test_clean_query_sends_only_coordinates_and_keeps_maven_qualifier(self):
        fake = FakeHttp({"results": [{}]})
        data = inventory()
        data["components"][0]["version"] = "32.0.1-jre"
        report = self.run_audit(fake, data)
        self.assertEqual("clean", report["status"])
        self.assertEqual(0, report["advisoryCount"])
        self.assertEqual([{"queries": [{"package": {"ecosystem": "Maven", "name": "example.test:library0"}, "version": "32.0.1-jre"}]}], fake.requests)

    def test_any_advisory_fails_without_severity_filter_and_versions_stay_distinct(self):
        fake = FakeHttp({"results": [{"vulns": [vuln()]}, {}]})
        data = inventory()
        data["components"].append({**data["components"][0], "version": "2.0.0"})
        report = self.run_audit(fake, data)
        self.assertEqual("vulnerable", report["status"])
        self.assertEqual(2, report["componentCount"])
        self.assertEqual(1, report["affectedComponentCount"])

    def test_duplicate_components_merge_configuration_memberships(self):
        data = inventory()
        data["components"].append({**data["components"][0], "configurations": [":app:releaseRuntimeClasspath"]})
        report = self.run_audit(FakeHttp({"results": [{"vulns": [vuln(), vuln()]}]}), data)
        self.assertEqual(1, report["componentCount"])
        self.assertEqual(1, report["advisoryCount"])
        self.assertEqual(2, len(report["findings"][0]["configurations"]))

    def test_shared_advisory_counts_both_affected_components(self):
        report = self.run_audit(FakeHttp({"results": [{"vulns": [vuln()]}, {"vulns": [vuln()]}]}), inventory(2))
        self.assertEqual(1, report["advisoryCount"])
        self.assertEqual(2, report["findingCount"])

    def test_batches_are_bounded_to_100(self):
        fake = FakeHttp({"results": [{}] * 100}, {"results": [{}]})
        self.assertEqual("clean", self.run_audit(fake, inventory(101))["status"])
        self.assertEqual([100, 1], [len(item["queries"]) for item in fake.requests])

    def test_pagination_only_requeries_pending_packages_and_preserves_mapping(self):
        fake = FakeHttp(
            {"results": [{"next_page_token": "first"}, {}, {"vulns": [vuln()], "next_page_token": "third"}]},
            {"results": [{"vulns": [vuln("OSV-2026-123")]}, {"next_page_token": "third-again"}]},
            {"results": [{"vulns": [vuln("GHSA-abcd-efgh-ijkl")]}]},
        )
        report = self.run_audit(fake, inventory(3))
        self.assertEqual([3, 2, 1], [len(item["queries"]) for item in fake.requests])
        self.assertEqual(["first", "third"], [item["page_token"] for item in fake.requests[1]["queries"]])
        self.assertEqual("example.test:library2", fake.requests[2]["queries"][0]["package"]["name"])
        self.assertEqual(["OSV-2026-123"], report["findings"][0]["advisories"])
        self.assertEqual(2, len(report["findings"][1]["advisories"]))

    def test_repeated_pagination_token_or_page_limit_is_error(self):
        repeated = {"results": [{"next_page_token": "repeat"}]}
        with self.assertRaises(checker.AuditError):
            self.run_audit(FakeHttp(repeated, repeated))
        with patch.object(checker, "MAX_PAGES", 1), self.assertRaises(checker.AuditError):
            self.run_audit(FakeHttp(repeated))

    def test_malformed_results_fail_closed(self):
        for response in ({}, {"results": []}, {"results": [{}, {}]}, {"results": None},
                         {"results": [None]}, {"results": [{"error": "unavailable"}]},
                         {"results": [{"vulns": None}]}, {"results": [{"vulns": [{}]}]},
                         {"results": [{"vulns": [{"id": "bad\nID"}]}]},
                         {"results": [{"next_page_token": None}]}):
            with self.subTest(response=response), self.assertRaises(checker.AuditError):
                self.run_audit(FakeHttp(response))

    def test_malformed_or_duplicate_json_fails_without_retry(self):
        for raw in (b"<html>failure</html>", b'{"results": [{}], "results": []}'):
            fake = FakeHttp(raw)
            with self.assertRaises(checker.AuditError):
                self.run_audit(fake)
            self.assertEqual(1, len(fake.requests))

    def test_http_429_and_503_retry_then_succeed_with_bounded_backoff(self):
        fake = FakeHttp(urllib.error.HTTPError(checker.ENDPOINT, 429, "rate", {}, None),
                        urllib.error.HTTPError(checker.ENDPOINT, 503, "busy", {}, None), {"results": [{}]})
        self.assertEqual("clean", self.run_audit(fake)["status"])
        self.assertEqual([1, 2], fake.sleeps)
        self.assertEqual(3, fake.client.requests)

    def test_http_400_and_redirect_do_not_retry(self):
        for status in (400, 302):
            fake = FakeHttp(urllib.error.HTTPError(checker.ENDPOINT, status, "failure", {}, None))
            with self.subTest(status=status), self.assertRaises(checker.AuditError):
                self.run_audit(fake)
            self.assertEqual([], fake.sleeps)
        self.assertIsNone(checker.NoRedirect().redirect_request(None, None, 302, "", {}, "https://elsewhere.test"))

    def test_network_error_never_produces_clean_result(self):
        fake = FakeHttp(*[urllib.error.URLError("offline")] * 3)
        with self.assertRaises(checker.AuditError):
            self.run_audit(fake)
        self.assertEqual(3, len(fake.requests))
        self.assertEqual([1, 2], fake.sleeps)

    def test_truncated_http_response_and_timeout_are_bounded_errors(self):
        for error in (http.client.IncompleteRead(b"partial", 100), TimeoutError()):
            fake = FakeHttp(error, error, error)
            with self.subTest(error=type(error).__name__), self.assertRaises(checker.AuditError):
                self.run_audit(fake)
            self.assertEqual(3, fake.client.requests)

    def test_oversized_response_is_rejected(self):
        with patch.object(checker, "MAX_JSON_BYTES", 10), self.assertRaises(checker.AuditError):
            self.run_audit(FakeHttp(b'{"results": [{}]}'))

    def test_incomplete_or_malformed_inventory_is_rejected(self):
        invalid = [None, {}, {**inventory(), "schemaVersion": True}, {**inventory(), "components": []},
                   {**inventory(), "configurations": []}]
        for field, value in (("version", "latest.release"), ("version", "1.+"), ("version", "[1,2)"), ("group", "bad\nname"),
                             ("configurations", []), ("configurations", ["not-resolved"])):
            data = inventory()
            data["components"][0][field] = value
            invalid.append(data)
        for data in invalid:
            with self.subTest(data=data), self.assertRaises(checker.AuditError):
                checker.validate_inventory(data)

    def test_cli_exit_codes_and_error_report_replace_previous_clean_report(self):
        with tempfile.TemporaryDirectory() as folder:
            source, output = Path(folder) / "input.json", Path(folder) / "report.json"
            source.write_text(json.dumps(inventory()))
            args = ["--input", str(source), "--output", str(output)]
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(0, checker.main(args, FakeHttp({"results": [{}]}).client))
                self.assertEqual(1, checker.main(args, FakeHttp({"results": [{"vulns": [vuln()]}]}).client))
                self.assertEqual("vulnerable", json.loads(output.read_text())["status"])
                self.assertEqual(2, checker.main(args, FakeHttp({"results": []}).client))
                self.assertEqual("error", json.loads(output.read_text())["status"])

    def test_same_input_output_is_rejected_without_overwriting_inventory(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "input.json"
            original = json.dumps(inventory())
            path.write_text(original)
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, checker.main(["--input", str(path), "--output", str(path)]))
            self.assertEqual(original, path.read_text())


if __name__ == "__main__":
    unittest.main()
