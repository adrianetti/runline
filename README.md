# Runline

A personal Android app for runs, walks and hikes. GPS recording, local history, satellite maps, route replay and GPX/CSV exports. Optional voice coaching and AI goals. English and Spanish.

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

## More

- [Usage](docs/USAGE.md) · [Development](docs/DEVELOPMENT.md) · [Repository checks](docs/CHECKS.md)
- [Maps](docs/MAPS.md) · [Coaching](docs/COACHING.md) · [AI](docs/AI.md) · [Goals](docs/GOALS.md) · [Exports](docs/EXPORTS.md)
- [Testing and known limits](docs/TESTING.md) · [Release history](docs/RELEASE-HISTORY.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

Prototype: controlled physical-phone field and battery tests remain outstanding.
