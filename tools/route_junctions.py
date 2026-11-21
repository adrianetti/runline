#!/usr/bin/env python3
import argparse
import itertools
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_routes import ROOT, Point, check_gpx, meters, write_index

OVERPASS = "https://overpass-api.de/api/interpreter"
WALKABLE = {
    "footway",
    "path",
    "track",
    "residential",
    "service",
    "living_street",
    "pedestrian",
    "steps",
    "unclassified",
    "tertiary",
    "secondary",
    "primary",
    "cycleway",
    "bridleway",
    "tertiary_link",
    "secondary_link",
    "primary_link",
    "road",
}
SNAP_M = 2.5
JUNCTION_MIN_DEGREE = 3


def overpass(bbox: tuple[float, float, float, float]) -> dict[str, Any]:
    south, west, north, east = bbox
    query = f'[out:json][timeout:120];(way["highway"]({south},{west},{north},{east}););out geom;'
    request = urllib.request.Request(
        OVERPASS,
        data=urllib.parse.urlencode({"data": query}).encode(),
        headers={"User-Agent": "Runline route tools (https://github.com/adrianetti/runline)"},
    )
    with urllib.request.urlopen(request, timeout=180) as reply:
        return json.load(reply)


def junctions(points: list[Point], osm: dict[str, Any]) -> list[list[float]]:
    coord, neighbours = {}, {}
    for way in osm.get("elements", []):
        tags = way.get("tags", {})
        if way.get("type") != "way" or tags.get("highway") not in WALKABLE:
            continue
        if tags.get("access") in ("private", "no") and tags.get("foot") not in ("yes", "designated", "permissive"):
            continue
        nodes = way["nodes"]
        for node, g in zip(nodes, way["geometry"], strict=False):
            coord[node] = (g["lat"], g["lon"])
        for a, b in itertools.pairwise(nodes):
            neighbours.setdefault(a, set()).add(b)
            neighbours.setdefault(b, set()).add(a)
    cells = {}
    for node, (lat, lon) in coord.items():
        cells.setdefault((round(lat, 4), round(lon, 4)), []).append(node)
    found = []
    for lat, lon, _ in points:
        near = [n for dy in (-1, 0, 1) for dx in (-1, 0, 1) for n in cells.get((round(lat + dy * 1e-4, 4), round(lon + dx * 1e-4, 4)), [])]
        degree = max((len(neighbours.get(n, ())) for n in near if meters(coord[n], (lat, lon)) <= SNAP_M), default=0)
        point = [round(lat, 6), round(lon, 6)]
        if degree >= JUNCTION_MIN_DEGREE and point not in found:
            found.append(point)
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description="Record where an open route meets other walkable OpenStreetMap ways.")
    parser.add_argument("route_id")
    parser.add_argument("--osm", type=Path, help="a saved Overpass JSON reply covering the route")
    args = parser.parse_args()
    index_path = ROOT / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    entry = next((r for r in index["routes"] if r["id"] == args.route_id), None)
    if entry is None:
        print(f"No route {args.route_id} in index.json", file=sys.stderr)
        return 1
    problems = []
    points = check_gpx(ROOT / entry["file"], problems)
    if points is None:
        print("\n".join(problems), file=sys.stderr)
        return 1
    pad = 0.0005
    bbox = (
        min(p[0] for p in points) - pad,
        min(p[1] for p in points) - pad,
        max(p[0] for p in points) + pad,
        max(p[1] for p in points) + pad,
    )
    osm = json.loads(args.osm.read_text()) if args.osm else overpass(bbox)
    entry["junctions"] = junctions(points, osm)
    write_index(index_path, index)
    print(f"{args.route_id}: {len(entry['junctions'])} junctions along {len(points)} track points")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
