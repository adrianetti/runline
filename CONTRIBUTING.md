# Contributing to Runline

Runline is a native Android activity recorder. Keep recording reliable, the interface quiet, and the user's data on their device.

- Start with [Development](docs/DEVELOPMENT.md), [Product](PRODUCT.md), and [Design](DESIGN.md).
- Open a focused issue or pull request describing the problem and observable result. Use synthetic GPS samples and screenshots.
- Keep English and Spanish resources aligned and use the existing Tabler icon family.
- Preserve original GPS fixes and recovery compatibility. Display smoothing must never rewrite recorded coordinates or heights.
- Add regression coverage for recording, storage, analysis and coaching changes. Run `./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug`, `python3 tools/check_resources.py`, and `python3 tools/import_icons.py --check`.
- Every applicable check must pass on the final PR commit before merging. A green earlier commit does not qualify.
- Never commit keys, signing material, real activity exports, health notes, photos or private logs. Diagnostic exports omit coordinates by default, but spoken text and timestamps can still be personal; inspect them before sharing.

Contributions are distributed under the project's [MIT license](LICENSE). Third-party code and remote imagery retain their own terms; see [notices](THIRD_PARTY_NOTICES.md).
