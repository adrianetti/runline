#!/usr/bin/env python3
"""Validate the open route catalog in routes/ (CI and contributors).

Checks the index schema, every file's SHA-256, GPX safety and privacy (no DOCTYPE, no
timestamps or device extensions), the ODbL licence element, and that distance and ascent
in the index match the GPX (same 3 m deadband as the app). Also refuses GPX files that
the index does not list. `--fix-hashes` rewrites SHA-256 values and computed stats.
"""
import argparse, hashlib, json, math, re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "routes"
NS = "{http://www.topografix.com/GPX/1/1}"
ID = re.compile(r"[a-z0-9][a-z0-9-]{2,79}")
FILE = re.compile(r"[a-z0-9][a-z0-9_-]*(/[a-z0-9][a-z0-9_-]*){0,4}\.gpx")
MAX_BYTES = 5 * 1024 * 1024

def meters(a, b):
    la, lb = math.radians(a[0]), math.radians(b[0])
    h = math.sin((lb - la) / 2) ** 2 + math.cos(la) * math.cos(lb) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(min(1.0, h)))

def stats(points):
    dist = 0.0; prev = None; gain = loss = 0.0; anchor = None
    for lat, lon, ele in points:
        if prev and (lat, lon) == prev[:2]: continue
        if prev: dist += meters(prev, (lat, lon))
        prev = (lat, lon)
        if anchor is None: anchor = ele
        elif abs(ele - anchor) >= 3:
            if ele > anchor: gain += ele - anchor
            else: loss += anchor - ele
            anchor = ele
    return dist, gain, loss

def check_gpx(path, problems):
    raw = path.read_bytes()
    if len(raw) > MAX_BYTES: problems.append(f"{path}: larger than 5 MB"); return None
    text = raw.decode("utf-8")
    if re.search(r"<!(DOCTYPE|ENTITY)", text, re.I): problems.append(f"{path}: DOCTYPE/ENTITY not allowed"); return None
    root = ET.fromstring(raw)
    if root.tag != NS + "gpx": problems.append(f"{path}: not GPX 1.1"); return None
    if root.find(f".//{NS}time") is not None: problems.append(f"{path}: contains timestamps (personal data)")
    if root.find(f".//{NS}trkpt/{NS}extensions") is not None: problems.append(f"{path}: track points carry device extensions")
    lic = root.find(f"{NS}metadata/{NS}copyright/{NS}license")
    if lic is None or "odbl" not in (lic.text or "").lower(): problems.append(f"{path}: metadata copyright must name the ODbL")
    if not (root.findtext(f"{NS}metadata/{NS}name") or "").strip(): problems.append(f"{path}: metadata name missing")
    points = []
    for p in root.iter(NS + "trkpt"):
        ele = p.findtext(NS + "ele")
        if ele is None: problems.append(f"{path}: every track point needs <ele>"); return None
        points.append((float(p.get("lat")), float(p.get("lon")), float(ele)))
    if len(points) < 2: problems.append(f"{path}: needs a track"); return None
    if not any((w.findtext(NS + "name") or "").strip() for w in root.iter(NS + "wpt")): problems.append(f"{path}: add named waypoints")
    return points

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fix-hashes", action="store_true")
    args = parser.parse_args()
    problems = []
    index_path = ROOT / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    if index.get("version") != 1 or index.get("license") != "ODbL-1.0" or "OpenStreetMap" not in index.get("attribution", ""):
        problems.append("index.json: version 1, license ODbL-1.0 and OpenStreetMap attribution are required")
    seen = set(); listed = set()
    for entry in index.get("routes", []):
        rid = entry.get("id", ""); f = entry.get("file", "")
        if not ID.fullmatch(rid) or rid in seen: problems.append(f"{rid}: bad or duplicate id")
        seen.add(rid)
        if not FILE.fullmatch(f) or ".." in f: problems.append(f"{rid}: bad file path {f}"); continue
        if entry.get("activity") not in ("hike", "walk", "run"): problems.append(f"{rid}: activity must be hike, walk or run")
        if not entry.get("name", {}).get("en"): problems.append(f"{rid}: an English name is required")
        path = ROOT / f; listed.add(path.resolve())
        if not path.is_file(): problems.append(f"{rid}: missing {f}"); continue
        sha = hashlib.sha256(path.read_bytes()).hexdigest()
        points = check_gpx(path, problems)
        if points is None: continue
        dist, gain, loss = stats(points)
        if args.fix_hashes:
            entry["sha256"] = sha; entry["distance_m"] = round(dist); entry["ascent_m"] = round(gain); entry["descent_m"] = round(loss)
            entry["start"] = [round(points[0][0], 6), round(points[0][1], 6)]
        if entry.get("sha256") != sha: problems.append(f"{rid}: sha256 does not match {f} (run with --fix-hashes)")
        if abs(entry.get("distance_m", 0) - dist) > max(20, dist * 0.01): problems.append(f"{rid}: distance_m {entry.get('distance_m')} vs GPX {dist:.0f}")
        if abs(entry.get("ascent_m", 0) - gain) > 5: problems.append(f"{rid}: ascent_m {entry.get('ascent_m')} vs GPX {gain:.0f}")
        start = entry.get("start", [0, 0])
        if meters(start, points[0][:2]) > 5: problems.append(f"{rid}: start does not match the first track point")
    for gpx in ROOT.rglob("*.gpx"):
        if gpx.resolve() not in listed: problems.append(f"{gpx.relative_to(ROOT)}: not listed in index.json")
    if args.fix_hashes:
        index_path.write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if problems:
        print("\n".join(problems), file=sys.stderr); return 1
    print(f"Verified {len(index.get('routes', []))} open routes")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
