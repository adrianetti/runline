#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections.abc import Sequence
from pathlib import Path
from typing import Any, NamedTuple

ROOT = Path(__file__).resolve().parents[1] / "routes"
NS = "{http://www.topografix.com/GPX/1/1}"
ID = re.compile(r"[a-z0-9][a-z0-9-]{2,79}")
FILE = re.compile(r"[a-z0-9][a-z0-9_-]*(/[a-z0-9][a-z0-9_-]*){0,4}\.gpx")
MAX_BYTES = 5 * 1024 * 1024
MAX_NOTE = 260
SPUR_MIN_M = 50
SPUR_RETRACE_M = 3
SPUR_SHORTEST_M = 2
MAX_PLACES = 50
FIRST_PRINTABLE_CODEPOINT = 32
MAX_JUNCTIONS = 5000
COORDINATE_LENGTH = 2
JUNCTION_TRACK_TOLERANCE_M = 25
ELEVATION_STEP_M = 3
MIN_TRACK_POINTS = 2
HEIGHT_TOLERANCE_M = 2
BBOX_TOLERANCE_DEGREES = 0.0002
SPUR_END_WAYPOINT_M = 30
ASCENT_TOLERANCE_M = 5
START_TOLERANCE_M = 5
ACTIVITIES = ("hike", "walk", "run")

type Point = tuple[float, float, float]
type Entry = dict[str, Any]


class TrackSummary(NamedTuple):
    distance: float
    gain: float
    loss: float
    lowest: float
    highest: float
    bbox: list[float]


def write_index(path: Path, index: dict[str, Any]) -> None:
    text = json.dumps(index, indent=2, ensure_ascii=False)
    text = re.sub(r"\[\s+(-?[\d.]+),\s+(-?[\d.]+)\s+\]", r"[\1, \2]", text)
    path.write_text(text + "\n", encoding="utf-8")


def waypoint_names(path: Path) -> tuple[list[str], list[tuple[float, float]]]:
    root = ET.fromstring(path.read_bytes())
    return [(w.findtext(NS + "name") or "").strip() for w in root.iter(NS + "wpt")], [
        (float(w.get("lat")), float(w.get("lon"))) for w in root.iter(NS + "wpt")
    ]


def spurs(points: Sequence[Sequence[float]]) -> list[tuple[int, float]]:
    out = []
    k = 1
    while k < len(points) - 1:
        tip = k
        far = k + 1
        if far < len(points) and meters(points[tip], points[far]) <= 1:
            far += 1
        r = 1
        while tip - r >= 0 and far + r - 1 < len(points) and meters(points[tip - r], points[far + r - 1]) <= SPUR_RETRACE_M:
            r += 1
        r -= 1
        if r >= 1:
            one_way = sum(meters(points[q], points[q + 1]) for q in range(tip - r, tip))
            if one_way >= SPUR_SHORTEST_M:
                out.append((tip, one_way))
                k = far + r
                continue
        k += 1
    return out


def check_places(rid: str, entry: Entry, names: list[str], problems: list[str]) -> None:
    places = entry.get("places", [])
    if not isinstance(places, list) or len(places) > MAX_PLACES:
        problems.append(f"{rid}: places must be a list of at most 50")
        return
    seen = set()
    for place in places:
        name = place.get("waypoint", "")
        if name not in names:
            problems.append(f"{rid}: place {name!r} is not a waypoint name in the GPX")
        if name in seen:
            problems.append(f"{rid}: place {name!r} is listed twice")
        seen.add(name)
        note = place.get("note", {})
        for lang in ("en", "es"):
            text = note.get(lang, "") if isinstance(note, dict) else ""
            if not (1 <= len(text.strip()) <= MAX_NOTE) or "<" in text or any(ord(c) < FIRST_PRINTABLE_CODEPOINT for c in text):
                problems.append(f"{rid}: place {name!r} needs a plain {lang} note of 1-{MAX_NOTE} characters")


def check_junctions(rid: str, entry: Entry, points: list[Point], problems: list[str]) -> None:
    junctions = entry.get("junctions")
    if junctions is None:
        return
    if not isinstance(junctions, list) or len(junctions) > MAX_JUNCTIONS:
        problems.append(f"{rid}: junctions must be a list of at most 5000")
        return
    for j in junctions:
        if not (isinstance(j, list) and len(j) == COORDINATE_LENGTH and all(isinstance(v, (int, float)) for v in j)):
            problems.append(f"{rid}: junction {j!r} must be [lat, lon]")
            continue
        if min(meters(j, p) for p in points) > JUNCTION_TRACK_TOLERANCE_M:
            problems.append(f"{rid}: junction {j} is not on the track")


def meters(a: Sequence[float], b: Sequence[float]) -> float:
    la, lb = math.radians(a[0]), math.radians(b[0])
    h = math.sin((lb - la) / 2) ** 2 + math.cos(la) * math.cos(lb) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(min(1.0, h)))


def stats(points: list[Point]) -> tuple[float, float, float]:
    dist = 0.0
    prev = None
    gain = loss = 0.0
    anchor = None
    for lat, lon, ele in points:
        if prev and (lat, lon) == prev[:2]:
            continue
        if prev:
            dist += meters(prev, (lat, lon))
        prev = (lat, lon)
        if anchor is None:
            anchor = ele
        elif abs(ele - anchor) >= ELEVATION_STEP_M:
            if ele > anchor:
                gain += ele - anchor
            else:
                loss += anchor - ele
            anchor = ele
    return dist, gain, loss


def check_gpx_metadata(path: Path, root: ET.Element, problems: list[str]) -> None:
    if root.find(f".//{NS}time") is not None:
        problems.append(f"{path}: contains timestamps (personal data)")
    if root.find(f".//{NS}trkpt/{NS}extensions") is not None:
        problems.append(f"{path}: track points carry device extensions")
    lic = root.find(f"{NS}metadata/{NS}copyright/{NS}license")
    if lic is None or "odbl" not in (lic.text or "").lower():
        problems.append(f"{path}: metadata copyright must name the ODbL")
    if not (root.findtext(f"{NS}metadata/{NS}name") or "").strip():
        problems.append(f"{path}: metadata name missing")


def track_points(path: Path, root: ET.Element, problems: list[str]) -> list[Point] | None:
    points = []
    for p in root.iter(NS + "trkpt"):
        ele = p.findtext(NS + "ele")
        if ele is None:
            problems.append(f"{path}: every track point needs <ele>")
            return None
        points.append((float(p.get("lat")), float(p.get("lon")), float(ele)))
    if len(points) < MIN_TRACK_POINTS:
        problems.append(f"{path}: needs a track")
        return None
    return points


def check_gpx(path: Path, problems: list[str]) -> list[Point] | None:
    raw = path.read_bytes()
    if len(raw) > MAX_BYTES:
        problems.append(f"{path}: larger than 5 MB")
        return None
    text = raw.decode("utf-8")
    if re.search(r"<!(DOCTYPE|ENTITY)", text, re.IGNORECASE):
        problems.append(f"{path}: DOCTYPE/ENTITY not allowed")
        return None
    root = ET.fromstring(raw)
    if root.tag != NS + "gpx":
        problems.append(f"{path}: not GPX 1.1")
        return None
    check_gpx_metadata(path, root, problems)
    points = track_points(path, root, problems)
    if points is None:
        return None
    if not any((w.findtext(NS + "name") or "").strip() for w in root.iter(NS + "wpt")):
        problems.append(f"{path}: add named waypoints")
    return points


def check_index_header(index: dict[str, Any], problems: list[str]) -> None:
    if index.get("version") != 1 or index.get("license") != "ODbL-1.0" or "OpenStreetMap" not in index.get("attribution", ""):
        problems.append("index.json: version 1, license ODbL-1.0 and OpenStreetMap attribution are required")


def check_listing(rid: str, file: str, entry: Entry, problems: list[str]) -> bool:
    if not FILE.fullmatch(file) or ".." in file:
        problems.append(f"{rid}: bad file path {file}")
        return False
    if entry.get("activity") not in ACTIVITIES:
        problems.append(f"{rid}: activity must be hike, walk or run")
    if not entry.get("name", {}).get("en"):
        problems.append(f"{rid}: an English name is required")
    return True


def check_git_status(rid: str, file: str, path: Path, problems: list[str]) -> None:
    tracked = subprocess.run(["git", "ls-files", "--error-unmatch", str(path)], cwd=ROOT, capture_output=True, check=False)
    ignored = subprocess.run(["git", "check-ignore", "-q", str(path)], cwd=ROOT, capture_output=True, check=False)
    if ignored.returncode == 0:
        problems.append(f"{rid}: {file} is git-ignored; it would not be published")
    elif tracked.returncode != 0:
        problems.append(f"{rid}: {file} is not added to git")


def summarize(points: list[Point]) -> TrackSummary:
    dist, gain, loss = stats(points)
    heights = [p[2] for p in points]
    bbox = [
        round(min(p[0] for p in points), 5),
        round(min(p[1] for p in points), 5),
        round(max(p[0] for p in points), 5),
        round(max(p[1] for p in points), 5),
    ]
    return TrackSummary(dist, gain, loss, min(heights), max(heights), bbox)


def record_summary(entry: Entry, sha: str, points: list[Point], summary: TrackSummary) -> None:
    entry["sha256"] = sha
    entry["distance_m"] = round(summary.distance)
    entry["ascent_m"] = round(summary.gain)
    entry["descent_m"] = round(summary.loss)
    entry["start"] = [round(points[0][0], 6), round(points[0][1], 6)]
    entry["min_elevation_m"] = round(summary.lowest)
    entry["max_elevation_m"] = round(summary.highest)
    entry["bbox"] = summary.bbox


def check_extent(rid: str, entry: Entry, summary: TrackSummary, problems: list[str]) -> None:
    if "min_elevation_m" in entry and (
        abs(entry["min_elevation_m"] - summary.lowest) > HEIGHT_TOLERANCE_M
        or abs(entry.get("max_elevation_m", 0) - summary.highest) > HEIGHT_TOLERANCE_M
    ):
        recorded = f"{entry['min_elevation_m']}-{entry.get('max_elevation_m')}"
        problems.append(f"{rid}: height range {recorded} vs GPX {summary.lowest:.0f}-{summary.highest:.0f}")
    if "bbox" in entry and any(abs(a - b) > BBOX_TOLERANCE_DEGREES for a, b in zip(entry["bbox"], summary.bbox, strict=False)):
        problems.append(f"{rid}: bbox does not match the track")


def check_spurs(rid: str, points: list[Point], names: list[str], waypoints: list[tuple[float, float]], problems: list[str]) -> None:
    for tip, one_way in spurs(points):
        near_name = any(n and meters(w, points[tip]) <= SPUR_END_WAYPOINT_M for n, w in zip(names, waypoints, strict=True))
        if one_way < SPUR_MIN_M or not near_name:
            problems.append(
                f"{rid}: dead-end spur of {one_way:.0f} m each way at track point {tip} ({points[tip][0]:.6f}, {points[tip][1]:.6f}); "
                "remove it, or end a deliberate out-and-back at a named waypoint"
            )


def check_recorded_totals(rid: str, file: str, entry: Entry, sha: str, summary: TrackSummary, points: list[Point]) -> list[str]:
    problems = []
    if entry.get("sha256") != sha:
        problems.append(f"{rid}: sha256 does not match {file} (run with --fix-hashes)")
    if abs(entry.get("distance_m", 0) - summary.distance) > max(20, summary.distance * 0.01):
        problems.append(f"{rid}: distance_m {entry.get('distance_m')} vs GPX {summary.distance:.0f}")
    if abs(entry.get("ascent_m", 0) - summary.gain) > ASCENT_TOLERANCE_M:
        problems.append(f"{rid}: ascent_m {entry.get('ascent_m')} vs GPX {summary.gain:.0f}")
    start = entry.get("start", [0, 0])
    if meters(start, points[0][:2]) > START_TOLERANCE_M:
        problems.append(f"{rid}: start does not match the first track point")
    return problems


def check_entry(entry: Entry, seen: set[str], listed: set[Path], problems: list[str], *, fix_hashes: bool) -> None:
    rid = entry.get("id", "")
    file = entry.get("file", "")
    if not ID.fullmatch(rid) or rid in seen:
        problems.append(f"{rid}: bad or duplicate id")
    seen.add(rid)
    if not check_listing(rid, file, entry, problems):
        return
    path = ROOT / file
    listed.add(path.resolve())
    if not path.is_file():
        problems.append(f"{rid}: missing {file}")
        return
    check_git_status(rid, file, path, problems)
    sha = hashlib.sha256(path.read_bytes()).hexdigest()
    points = check_gpx(path, problems)
    if points is None:
        return
    summary = summarize(points)
    if fix_hashes:
        record_summary(entry, sha, points, summary)
    check_extent(rid, entry, summary, problems)
    names, waypoints = waypoint_names(path)
    check_spurs(rid, points, names, waypoints, problems)
    check_places(rid, entry, names, problems)
    check_junctions(rid, entry, points, problems)
    problems.extend(check_recorded_totals(rid, file, entry, sha, summary, points))


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate the open route catalog in routes/.")
    parser.add_argument("--fix-hashes", action="store_true")
    args = parser.parse_args()
    problems = []
    index_path = ROOT / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    check_index_header(index, problems)
    seen = set()
    listed = set()
    for entry in index.get("routes", []):
        check_entry(entry, seen, listed, problems, fix_hashes=args.fix_hashes)
    problems.extend(f"{gpx.relative_to(ROOT)}: not listed in index.json" for gpx in ROOT.rglob("*.gpx") if gpx.resolve() not in listed)
    if args.fix_hashes:
        write_index(index_path, index)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"Verified {len(index.get('routes', []))} open routes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
