# Third-party notices

## Tabler Icons

Runline uses a subset of the outline icons from [Tabler Icons](https://github.com/tabler/tabler-icons), licensed under the MIT License. This is the app's only pictogram family, including its running-figure launcher and foreground-notification glyph.

The original SVG files and license are retained in `third_party/tabler-icons/`. They are pinned to upstream commit `ed177a085b652fe04ec4f37ced867c94621e7840`. `provenance.json` records each original source URL and SHA-256 checksum.

`tools/import_icons.py` converts the original SVG path geometry into Android VectorDrawables with the original 2-unit stroke, round caps and round joins. UI icons inherit a monochrome tint from the component using them. The launcher adaptation puts the same running figure in white on a dark square.

To regenerate offline, run `python3 tools/import_icons.py`. To verify the checked-in resources, run `python3 tools/import_icons.py --check`. `--fetch` downloads the same pinned originals; it does not silently follow the latest upstream revision.

The full upstream MIT notice is in [third_party/tabler-icons/LICENSE](third_party/tabler-icons/LICENSE).

## MapLibre Native

Runline uses [MapLibre Native Android 11.13.5](https://github.com/maplibre/maplibre-native/tree/android-v11.13.5) under the BSD-2-Clause license. The full upstream notice is bundled as `app/src/main/assets/licenses/maplibre-native.txt`. MapLibre renders maps locally; imagery is supplied separately by the providers below.

## Satellite imagery and map labels

- Spain imagery: © IGN / PNOA, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), [official service metadata](https://www.idee.es/csw-codsi-idee/srv/api/records/spaignwmts_pnoa-ma).
- Global imagery: EOxCloudless by [EOX](https://maps.eox.at/), containing modified Copernicus Sentinel data 2025, [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). This personal build is noncommercial. Global imagery is approximately 10 metres per pixel; Spain has more detailed PNOA aerial coverage.
- Label overlay: data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright); rendering © [EOX](https://eox.at/) and [MapServer](https://github.com/mapserver/basemaps).
- Spain coverage geometry: [Natural Earth](https://www.naturalearthdata.com/about/terms-of-use/), public domain. Extracted from `ne_10m_admin_0_countries.geojson`; this approximate national outline selects imagery, never changes the GPS recording.

The map's visible attribution control links to these sources and license terms. Tile requests reveal the viewed map area and the requesting network address to the imagery providers; recording data and replay calculations remain on the phone. See [map coverage and technical details](docs/MAPS.md).
