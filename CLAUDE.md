# Runline — working notes for Claude

Native Android (Kotlin, Android Views, Material, MapLibre 11.13.5) app that records runs, walks and hikes. It keeps original GPS samples on the phone and shows pace, splits, elevation, satellite replay and optional voice and AI coaching. It began on 2026-10-31 because Strava locks API and analytics behind a subscription. The owner builds it for personal half-marathon training and intends a public product later. Repo: `adrianetti/runline`, public, MIT since v0.13. Current release: **v0.15.0 (code 17)**.

Read first: [PRODUCT.md](PRODUCT.md) (behaviour contract), [DESIGN.md](DESIGN.md) (tokens, type, island layout), [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), [docs/CHECKS.md](docs/CHECKS.md), [docs/TESTING.md](docs/TESTING.md) (per-release evidence and known limits).

## Layout

`app/src/main/java/com/runline/app/`
- `tracking/` – `TrackingService` foreground GPS service, notification actions, recovery. Most safety-critical code.
- `data/` – `DiskRunStore`: per-point journal, checkpoints, atomic sidecars for activity metadata.
- `core/` – pure, JVM-tested logic: metrics, replay, slope and terrain trend, coach engine, speech scheduling, lap analysis, smart goals.
- `coaching/` – TTS speech and audio focus (`MAY_DUCK`).
- `ai/` – Anthropic provider (Haiku/Sonnet/Opus 5.5 choices), live advice gate, smart goal, review queue and cards, usage metrics.
- `terrain/` – IGN MDT05 for Spain, global Mapzen/AWS Terrarium tiles, offline area prep and cache.
- `ui/` – sheets, islands, `SatelliteMapView`, replay, charts, rolling numbers.
- `sharing/` – GPX, CSV and ZIP export and naming. `diagnostics/` – coach speech log.

Strings are split per feature (`strings_*_vNN.xml`). Every key must exist in both `values/` (English) and `values-es/` (Spanish). Icons come only from Tabler outline, pinned via `tools/import_icons.py`.

## Build and checks

JDK 17, SDK 37, Build Tools 36.0.0, AGP 9.5.0, Kotlin 2.4.21, Gradle 9.8.2. minSdk 26, targetSdk 34.

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
python3 tools/check_resources.py
python3 tools/import_icons.py --check
```

The repo has six required GitHub checks: Android build, dependency advisories, resources, actionlint, Gitleaks and Semgrep security. See `.github/workflows/`.

## How work has been done (Codex era, v0.1 to v0.15)

- The owner field-tests each build on an Android phone (Samsung) during real runs. Then they send one long message of feedback and ideas. Each message becomes the next minor version.
- One branch per release, named `codex/<topic>-vNN`. Open a PR and merge only after **every check passes on the exact PR head**. Never push directly to main.
- Each release bumps `versionCode`/`versionName` in `app/build.gradle.kts`. It updates `docs/TESTING.md`, `docs/RELEASE-HISTORY.md` and the relevant PRODUCT.md section. It publishes a **signed** APK to GitHub Releases. The owner installs over the existing app.
- Signing uses the dedicated v0.2 key in the main checkout's ignored `.signing/`. Never change it, because updates must keep installed recordings. Each release verifies that existing app files stay byte-for-byte unchanged after an in-place update on the API 34 emulator.
- Tests use synthetic GPS fixtures. Real GPX files and keys never enter Git.

## Invariants

- Never rewrite recorded GPS coordinates or heights. Smoothing and terrain apply only to display and analysis.
- Recording must never depend on maps, terrain, network or AI.
- When terrain or grade is uncertain, show unknown and give no hill coaching. Do not guess.
- AI is opt-in and off by default, and uses the user's own Anthropic key. No API keys in recordings, exports or logs.
- Keep the UI monochrome with very short copy, Tabler icons and grade colours only for slope. Pace in min/km is the hero number.
- Keep English and Spanish in parity. The app follows the phone language.

## Known gaps and open threads

- Physical-phone field, battery, headphone and Spotify behaviour, and paid-AI-in-production behaviour, are largely unverified. Most evidence comes from the emulator.
- Terrain covers one saved 5 km area. Downloads run in the foreground with manual resume. Satellite imagery is not offline. Polar caps are excluded.
- Global EOX imagery is licensed for noncommercial use only, which blocks a commercial or Play launch until resolved.
- Google Play is deferred until the owner has a developer account (plan: `docs/PUBLISHING.md`). OpenAI provider and watch support are planned. Strava integration is out of scope.
- Open Dependabot PRs #16–#20 include a MapLibre 11 to 13 major bump.
- PRODUCT.md "Stack" still says 0.13.0/code 15, and "Open Decisions" still says the repo is private. Both are stale.
