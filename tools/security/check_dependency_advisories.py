#!/usr/bin/env python3
import argparse
import http.client
import json
import re
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Callable
from datetime import UTC, datetime
from http import HTTPStatus
from pathlib import Path
from typing import IO, Any, override

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
MAX_COORDINATE_LENGTH = 256
MAX_PAGE_TOKEN_LENGTH = 16_384
FIRST_SERVER_ERROR = 500
LAST_SERVER_ERROR = 599
EXIT_CLEAN = 0
EXIT_VULNERABLE = 1
EXIT_ERROR = 2
RANGE_DASH = "\u2013"

type Component = dict[str, Any]
type Query = dict[str, Any]
type OpenRequest = Callable[..., http.client.HTTPResponse]


class AuditError(Exception):
    pass


def _object_without_duplicates(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise AuditError("JSON contains duplicate object keys")
        result[key] = value
    return result


def decode_json(raw: bytes, source: str) -> object:
    if len(raw) > MAX_JSON_BYTES:
        raise AuditError(f"{source} exceeds the JSON size limit")
    try:
        return json.loads(raw, object_pairs_hook=_object_without_duplicates)
    except (UnicodeError, ValueError, RecursionError) as error:
        raise AuditError(f"{source} is not valid JSON") from error


def read_inventory(path: Path) -> tuple[list[Component], list[str]]:
    try:
        with path.open("rb") as source:
            raw = source.read(MAX_JSON_BYTES + 1)
    except OSError as error:
        raise AuditError("Cannot read the resolved dependency inventory") from error
    return validate_inventory(decode_json(raw, "Dependency inventory"))


def validate_configurations(data: object) -> list[str]:
    if not isinstance(data, dict) or type(data.get("schemaVersion")) is not int or data["schemaVersion"] != 1:
        raise AuditError("Dependency inventory must use schemaVersion 1")
    configurations = data.get("configurations")
    if (
        not isinstance(configurations, list)
        or not configurations
        or any(not isinstance(item, str) or not item.strip() for item in configurations)
    ):
        raise AuditError("Dependency inventory must list resolved configuration names")
    if len(set(configurations)) != len(configurations):
        raise AuditError("Dependency inventory has duplicate configuration names")
    return configurations


def validate_component(item: object, known_configurations: set[str]) -> tuple[tuple[str, str, str], list[str]]:
    if not isinstance(item, dict):
        raise AuditError("Dependency inventory contains a malformed component")
    for field in ("group", "name", "version"):
        value = item.get(field)
        if not isinstance(value, str) or len(value) > MAX_COORDINATE_LENGTH or not COORDINATE_PART.fullmatch(value):
            raise AuditError(f"Dependency inventory has an invalid Maven {field}")
    if item["version"].startswith("latest.") or item["version"].endswith("+"):
        raise AuditError("Dependency inventory contains an unresolved version selector")
    memberships = item.get("configurations")
    if (
        not isinstance(memberships, list)
        or not memberships
        or any(not isinstance(value, str) or value not in known_configurations for value in memberships)
    ):
        raise AuditError("Every Maven component must belong to a resolved configuration")
    return (item["group"], item["name"], item["version"]), memberships


def validate_inventory(data: object) -> tuple[list[Component], list[str]]:
    configurations = validate_configurations(data)
    components = data.get("components")
    if not isinstance(components, list) or not 1 <= len(components) <= MAX_COMPONENTS:
        raise AuditError(f"Dependency inventory must contain 1{RANGE_DASH}{MAX_COMPONENTS} resolved Maven components")
    known_configurations = set(configurations)
    merged = {}
    for item in components:
        key, memberships = validate_component(item, known_configurations)
        merged.setdefault(key, set()).update(memberships)
    return [
        {"group": group, "name": name, "version": version, "configurations": sorted(memberships)}
        for (group, name, version), memberships in sorted(merged.items())
    ], sorted(configurations)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    @override
    def redirect_request(
        self,
        request: urllib.request.Request,
        fp: IO[bytes],
        code: int,
        message: str,
        headers: http.client.HTTPMessage,
        new_url: str,
    ) -> urllib.request.Request | None:
        return None


class OsvClient:
    def __init__(self, open_request: OpenRequest | None = None, sleep: Callable[[float], object] = time.sleep) -> None:
        self.open_request = open_request or urllib.request.build_opener(NoRedirect()).open
        self.sleep = sleep
        self.requests = 0

    def query_batch(self, queries: list[Query]) -> object:
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
                    if response.status != HTTPStatus.OK:
                        raise AuditError("OSV returned an unexpected HTTP status")
                    raw = response.read(MAX_JSON_BYTES + 1)
                return decode_json(raw, "OSV response")
            except urllib.error.HTTPError as error:
                retryable = error.code in (408, 429) or FIRST_SERVER_ERROR <= error.code <= LAST_SERVER_ERROR
                if not retryable or attempt == MAX_ATTEMPTS - 1:
                    raise AuditError(f"OSV HTTP request failed (status {error.code})") from error
            except (urllib.error.URLError, TimeoutError, ConnectionError, OSError, http.client.HTTPException) as error:
                if attempt == MAX_ATTEMPTS - 1:
                    raise AuditError("OSV request failed after bounded retries") from error
            self.sleep(2**attempt)
        raise AuditError("OSV request did not complete")


def query_for(component: Component, token: str | None = None) -> Query:
    query = {"package": {"ecosystem": "Maven", "name": f"{component['group']}:{component['name']}"}, "version": component["version"]}
    if token is not None:
        query["page_token"] = token
    return query


def batch_results(response: object, expected: int) -> list[object]:
    if not isinstance(response, dict) or set(response) != {"results"}:
        raise AuditError("OSV response must contain only a results array")
    results = response["results"]
    if not isinstance(results, list) or len(results) != expected:
        raise AuditError("OSV result count does not match the submitted queries")
    return results


def advisory_ids(result: object) -> list[str]:
    if not isinstance(result, dict) or set(result) - {"vulns", "next_page_token"}:
        raise AuditError("OSV returned a malformed query result")
    vulnerabilities = result.get("vulns", [])
    if not isinstance(vulnerabilities, list):
        raise AuditError("OSV vulnerabilities must be an array")
    ids = []
    for vulnerability in vulnerabilities:
        if (
            not isinstance(vulnerability, dict)
            or not isinstance(vulnerability.get("id"), str)
            or not ADVISORY_ID.fullmatch(vulnerability["id"])
        ):
            raise AuditError("OSV returned an invalid advisory ID")
        ids.append(vulnerability["id"])
    return ids


def next_page_token(result: dict[str, object], seen_tokens: set[str], pages: int) -> str:
    token = result.get("next_page_token", "")
    if not isinstance(token, str) or len(token) > MAX_PAGE_TOKEN_LENGTH:
        raise AuditError("OSV returned an invalid pagination token")
    if token:
        if token in seen_tokens or pages >= MAX_PAGES:
            raise AuditError("OSV pagination repeated or exceeded its page limit")
        seen_tokens.add(token)
    return token


def audit_batch(components: list[Component], indexes: range, client: OsvClient, findings: list[set[str]]) -> None:
    pending = [(index, None) for index in indexes]
    seen_tokens = {index: set() for index, _ in pending}
    pages = {index: 0 for index, _ in pending}
    while pending:
        response = client.query_batch([query_for(components[index], token) for index, token in pending])
        next_pending = []
        for (index, _), result in zip(pending, batch_results(response, len(pending)), strict=True):
            findings[index].update(advisory_ids(result))
            if sum(map(len, findings)) > MAX_FINDINGS:
                raise AuditError("OSV findings exceed the supported limit")
            pages[index] += 1
            token = next_page_token(result, seen_tokens[index], pages[index])
            if token:
                next_pending.append((index, token))
        pending = next_pending


def audit(components: list[Component], configurations: list[str], client: OsvClient) -> dict[str, object]:
    findings = [set() for _ in components]
    for offset in range(0, len(components), BATCH_SIZE):
        audit_batch(components, range(offset, min(offset + BATCH_SIZE, len(components))), client, findings)
    affected = [
        {
            "package": f"{component['group']}:{component['name']}",
            "version": component["version"],
            "configurations": component["configurations"],
            "advisories": sorted(ids),
        }
        for component, ids in zip(components, findings, strict=True)
        if ids
    ]
    return {
        "schemaVersion": 1,
        "status": "vulnerable" if affected else "clean",
        "source": ENDPOINT,
        "checkedAt": datetime.now(UTC).isoformat(),
        "componentCount": len(components),
        "configurationCount": len(configurations),
        "requestCount": client.requests,
        "advisoryCount": len(set().union(*findings)),
        "findingCount": sum(map(len, findings)),
        "affectedComponentCount": len(affected),
        "findings": affected,
    }


def print_summary(report: dict[str, Any]) -> None:
    totals = f"{report['componentCount']} Maven versions, {report['configurationCount']} configurations"
    print(f"Dependency audit: {totals}, {report['findingCount']} findings ({report['advisoryCount']} distinct advisories)")
    for finding in report["findings"]:
        print(f"  {finding['package']}@{finding['version']}: {', '.join(finding['advisories'])}")


def main(argv: list[str] | None = None, client: OsvClient | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check a resolved Gradle Maven inventory against OSV.")
    parser.add_argument("--input", required=True, type=Path, help="Resolved Gradle dependency inventory JSON")
    parser.add_argument("--output", required=True, type=Path, help="Audit report JSON (written on success and failure)")
    args = parser.parse_args(argv)
    if args.input.resolve() == args.output.resolve():
        print("Dependency audit failed: input and output must be different files", file=sys.stderr)
        return EXIT_ERROR
    try:
        components, configurations = read_inventory(args.input)
        report = audit(components, configurations, client or OsvClient())
        exit_code = EXIT_VULNERABLE if report["findings"] else EXIT_CLEAN
    except AuditError as error:
        report = {"schemaVersion": 1, "status": "error", "error": str(error)}
        exit_code = EXIT_ERROR
    try:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except OSError:
        print("Dependency audit failed: cannot write report", file=sys.stderr)
        return EXIT_ERROR
    if exit_code == EXIT_ERROR:
        print(f"Dependency audit failed: {report['error']}", file=sys.stderr)
    else:
        print_summary(report)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
