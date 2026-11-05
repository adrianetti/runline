# Runline

An open-source Android app for runs, walks and hikes. GPS recording, local history, satellite maps, route replay and GPX/CSV exports. Optional voice coaching and AI goals. English and Spanish.

<table><tr><td><img src="docs/images/replay-es.png" width="250" alt="Runline satellite replay with large pace and a compact terrain profile"></td><td><img src="docs/images/review-es.png" width="250" alt="Short Spanish review with achievement, focus and next session"></td><td><img src="docs/images/coach-log-es.png" width="250" alt="Private coach timeline with spoken messages and replay shortcuts"></td></tr></table>

Actual app screens with synthetic demo data. [Image details](docs/images/README.md).

**New in 0.14:** terrain-map elevations replace unreliable GPS heights in recording, replay and coaching. Nearby areas cache for offline use; missing or ambiguous terrain stays unknown. Mainland Spain and Balearic Islands initially. [Terrain and accuracy](docs/TERRAIN.md).

## Get started

1. Install the APK from [Releases](https://github.com/adrianetti/runline/releases/latest) on Android 8.0+.
2. Choose **Run**, **Walk** or **Hike**, allow precise location, and tap **Start** outdoors.
3. **Pause**, **Resume** or **Finish**; find saved recordings in **Activities**.

Recordings stay on your phone. **Export important activities before uninstalling or clearing app data.** Maps need internet; AI is off by default and uses your own Anthropic key.

## Build

Use JDK 17, Android SDK 37.0 and Build Tools 36.0.0. Set `ANDROID_HOME` to your SDK.

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

## Contribute

[MIT licensed](LICENSE) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md). Third-party assets and map services keep their own licenses. Global EOX imagery has noncommercial restrictions; source availability does not remove those terms.

Google Play publication is deferred until a developer account and the remaining release checks are ready. Signed GitHub APKs remain the update channel.

## More

- [Usage](docs/USAGE.md) · [Development](docs/DEVELOPMENT.md) · [Repository checks](docs/CHECKS.md)
- [Maps](docs/MAPS.md) · [Coaching](docs/COACHING.md) · [AI](docs/AI.md) · [Goals](docs/GOALS.md) · [Exports](docs/EXPORTS.md)
- [Coach diagnostics](docs/DIAGNOSTICS.md) · [Distribution and Google Play plan](docs/PUBLISHING.md)
- [Testing and known limits](docs/TESTING.md) · [Release history](docs/RELEASE-HISTORY.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

Prototype: controlled physical-phone field and battery tests remain outstanding.
