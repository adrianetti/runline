# Third-party notices

## Tabler Icons

Runline uses a subset of the outline icons from [Tabler Icons](https://github.com/tabler/tabler-icons), licensed under the MIT License. This is the app's only pictogram family, including its running-figure launcher and foreground-notification glyph.

The original SVG files and license are retained in `third_party/tabler-icons/`. They are pinned to upstream commit `ed177a085b652fe04ec4f37ced867c94621e7840`. `provenance.json` records each original source URL and SHA-256 checksum.

`tools/import_icons.py` converts the original SVG path geometry into Android VectorDrawables with the original 2-unit stroke, round caps and round joins. UI icons inherit a monochrome tint from the component using them. The launcher adaptation puts the same running figure in white on a dark square.

To regenerate offline, run `python3 tools/import_icons.py`. To verify the checked-in resources, run `python3 tools/import_icons.py --check`. `--fetch` downloads the same pinned originals; it does not silently follow the latest upstream revision.

The full upstream MIT notice is in [third_party/tabler-icons/LICENSE](third_party/tabler-icons/LICENSE).
