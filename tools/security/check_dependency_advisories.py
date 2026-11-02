#!/usr/bin/env python3
"""Check a resolved Gradle Maven inventory against OSV; no credentials required.

OSV performs ecosystem-aware version matching (including Maven qualifiers).
Only public Maven coordinates and versions leave this process. The inventory
must come from a successful, complete Gradle resolution, not a lockfile guess.
See https://google.github.io/osv.dev/post-v1-querybatch/ for pagination semantics.
"""
import argparse
from datetime import datetime, timezone
import http.client
import json
from pathlib import Path
import re
import socket
import sys
import time
import urllib.error
import urllib.request


ENDPOINT = "https://api.osv.dev/v1/querybatch"
BATCH_SIZE = 100
TIMEOUT_SECONDS = 30
MAX_ATTEMPTS = 3
MAX_PAGES = 100
MAX_COMPONENTS = 10_000
MAX_JSON_BYTES = 10 * 1024 * 1024
MAX_FINDINGS = 100_000
COORDINATE_PART = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.+\-]*\Z")
ADVISORY_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.:\-]{1,199}\Z")


class AuditError(Exception):
    """A failed audit must never be interpreted as a clean dependency graph."""


def _object_without_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AuditError("JSON contains duplicate object keys")
        result[key] = value
    return result


def decode_json(raw, source):
    if len(raw) > MAX_JSON_BYTES:
        raise AuditError(f"{source} exceeds the JSON size limit")
    try:
        return json.loads(raw, object_pairs_hook=_object_without_duplicates)
    except (UnicodeError, ValueError, RecursionError) as error:
        raise AuditError(f"{source} is not valid JSON") from error


def read_inventory(path):
    try:
        with path.open("rb") as source:
            raw = source.read(MAX_JSON_BYTES + 1)
    except OSError as error:
        raise AuditError("Cannot read the resolved dependency inventory") from error
    return validate_inventory(decode_json(raw, "Dependency inventory"))


def validate_inventory(data):
    if not isinstance(data, dict) or type(data.get("schemaVersion")) is not int or data["schemaVersion"] != 1:
        raise AuditError("Dependency inventory must use schemaVersion 1")
    configurations = data.get("configurations")
    if not isinstance(configurations, list) or not configurations or any(
        not isinstance(item, str) or not item.strip() for item in configurations
    ):
        raise AuditError("Dependency inventory must list resolved configuration names")
    if len(set(configurations)) != len(configurations):
        raise AuditError("Dependency inventory has duplicate configuration names")
    components = data.get("components")
    if not isinstance(components, list) or not 1 <= len(components) <= MAX_COMPONENTS:
        raise AuditError("Dependency inventory must contain 1–10000 resolved Maven components")
    known_configurations = set(configurations)
    merged = {}
    for item in components:
        if not isinstance(item, dict):
            raise AuditError("Dependency inventory contains a malformed component")
        for field in ("group", "name", "version"):
            value = item.get(field)
            if not isinstance(value, str) or len(value) > 256 or not COORDINATE_PART.fullmatch(value):
                raise AuditError(f"Dependency inventory has an invalid Maven {field}")
        if item["version"].startswith("latest.") or item["version"].endswith("+"):
            raise AuditError("Dependency inventory contains an unresolved version selector")
        memberships = item.get("configurations")
        if not isinstance(memberships, list) or not memberships or any(
            not isinstance(value, str) or value not in known_configurations for value in memberships
        ):
            raise AuditError("Every Maven component must belong to a resolved configuration")
        key = (item["group"], item["name"], item["version"])
        merged.setdefault(key, set()).update(memberships)
    return [
        {"group": group, "name": name, "version": version, "configurations": sorted(memberships)}
        for (group, name, version), memberships in sorted(merged.items())
    ], sorted(configurations)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        # Coordinates should only be submitted to the documented OSV endpoint.
        return None


class OsvClient:
    def __init__(self, open_request=None, sleep=time.sleep):
        self.open_request = open_request or urllib.request.build_opener(NoRedirect()).open
        self.sleep = sleep
        self.requests = 0

    def query_batch(self, queries):
        if not 1 <= len(queries) <= BATCH_SIZE:
            raise AuditError("OSV batch size is outside the supported bounds")
        request = urllib.request.Request(
            ENDPOINT,
            data=json.dumps({"queries": queries}, separators=(",", ":")).encode("utf-8"),
            headers={"Content-Type": "application/json", "Accept": "application/json", "User-Agent": "Runline-dependency-audit/1"},
            method="POST",
        )
        for attempt in range(MAX_ATTEMPTS):
            self.requests += 1
            try:
                with self.open_request(request, timeout=TIMEOUT_SECONDS) as response:
                    if response.status != 200:
                        raise AuditError("OSV returned an unexpected HTTP status")
                    raw = response.read(MAX_JSON_BYTES + 1)
                return decode_json(raw, "OSV response")
            except urllib.error.HTTPError as error:
                retryable = error.code in (408, 429) or 500 <= error.code <= 599
                if not retryable or attempt == MAX_ATTEMPTS - 1:
                    raise AuditError(f"OSV HTTP request failed (status {error.code})") from error
            except (urllib.error.URLError, TimeoutError, socket.timeout, ConnectionError, OSError, http.client.HTTPException) as error:
                if attempt == MAX_ATTEMPTS - 1:
                    raise AuditError("OSV request failed after bounded retries") from error
            self.sleep(2 ** attempt)
        raise AuditError("OSV request did not complete")


def query_for(component, token=None):
    query = {"package": {"ecosystem": "Maven", "name": f"{component['group']}:{component['name']}"}, "version": component["version"]}
    if token is not None:
        query["page_token"] = token
    return query


def audit(components, configurations, client):
    findings = [set() for _ in components]
    for offset in range(0, len(components), BATCH_SIZE):
        pending = [(index, None) for index in range(offset, min(offset + BATCH_SIZE, len(components)))]
        seen_tokens = {index: set() for index, _ in pending}
        pages = {index: 0 for index, _ in pending}
        while pending:
            response = client.query_batch([query_for(components[index], token) for index, token in pending])
            if not isinstance(response, dict) or set(response) != {"results"}:
                raise AuditError("OSV response must contain only a results array")
            results = response["results"]
            if not isinstance(results, list) or len(results) != len(pending):
                raise AuditError("OSV result count does not match the submitted queries")
            next_pending = []
            for (index, _), result in zip(pending, results):
                if not isinstance(result, dict) or set(result) - {"vulns", "next_page_token"}:
                    raise AuditError("OSV returned a malformed query result")
                vulnerabilities = result.get("vulns", [])
                if not isinstance(vulnerabilities, list):
                    raise AuditError("OSV vulnerabilities must be an array")
                for vulnerability in vulnerabilities:
                    if not isinstance(vulnerability, dict) or not isinstance(vulnerability.get("id"), str) or not ADVISORY_ID.fullmatch(vulnerability["id"]):
                        raise AuditError("OSV returned an invalid advisory ID")
                    # No severity threshold or exception list: every returned ID fails.
                    findings[index].add(vulnerability["id"])
                if sum(map(len, findings)) > MAX_FINDINGS:
                    raise AuditError("OSV findings exceed the supported limit")
                pages[index] += 1
                token = result.get("next_page_token", "")
                if not isinstance(token, str) or len(token) > 16_384:
                    raise AuditError("OSV returned an invalid pagination token")
                if token:
                    if token in seen_tokens[index] or pages[index] >= MAX_PAGES:
                        raise AuditError("OSV pagination repeated or exceeded its page limit")
                    seen_tokens[index].add(token)
                    next_pending.append((index, token))
            pending = next_pending
    affected = [
        {"package": f"{component['group']}:{component['name']}", "version": component["version"],
         "configurations": component["configurations"], "advisories": sorted(ids)}
        for component, ids in zip(components, findings) if ids
    ]
    return {
        "schemaVersion": 1,
        "status": "vulnerable" if affected else "clean",
        "source": ENDPOINT,
        "checkedAt": datetime.now(timezone.utc).isoformat(),
        "componentCount": len(components),
        "configurationCount": len(configurations),
        "requestCount": client.requests,
        "advisoryCount": len(set().union(*findings)),
        "findingCount": sum(map(len, findings)),
        "affectedComponentCount": len(affected),
        "findings": affected,
    }


def main(argv=None, client=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="Resolved Gradle dependency inventory JSON")
    parser.add_argument("--output", required=True, type=Path, help="Audit report JSON (written on success and failure)")
    args = parser.parse_args(argv)
    if args.input.resolve() == args.output.resolve():
        print("Dependency audit failed: input and output must be different files", file=sys.stderr)
        return 2
    try:
        components, configurations = read_inventory(args.input)
        report = audit(components, configurations, client or OsvClient())
        exit_code = 1 if report["findings"] else 0
    except AuditError as error:
        report = {"schemaVersion": 1, "status": "error", "error": str(error)}
        exit_code = 2
    try:
        # Never leave a previous clean report behind after an unsuccessful audit.
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except OSError:
        print("Dependency audit failed: cannot write report", file=sys.stderr)
        return 2
    if exit_code == 2:
        print(f"Dependency audit failed: {report['error']}", file=sys.stderr)
    else:
        print(f"Dependency audit: {report['componentCount']} Maven versions, {report['configurationCount']} configurations, {report['findingCount']} findings ({report['advisoryCount']} distinct advisories)")
        for finding in report["findings"]:
            print(f"  {finding['package']}@{finding['version']}: {', '.join(finding['advisories'])}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
