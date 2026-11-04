# Development

Runline uses Kotlin and Android Views. It is privately sideloaded; Play Store distribution is not configured.

## Build

Install JDK 17, Android SDK Platform 37 and Build Tools 36.0.0. Set `ANDROID_HOME`, or create an untracked `local.properties` with `sdk.dir=/absolute/path/to/Android/sdk`.

The wrapper pins Gradle 9.8.2; the build pins AGP 9.5.0 and Kotlin 2.4.21 through AGP's built-in Kotlin support. Compile SDK is 37; target SDK remains 34 and minimum SDK remains 26 (Android 8.0). [AGP 9.5 supports API 37](https://developer.android.com/build/releases/agp-9-5-0-release-notes#compatibility).

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

The development APK is `app/build/outputs/apk/debug/app-debug.apk`. Test/lint reports are under `app/build/reports/` and `app/build/test-results/`. See [repository checks](CHECKS.md) for resources, workflow lint, dependency and security checks, including the known upstream Gradle limitation.

## Install and signing

Use the APK from the private [Releases page](https://github.com/adrianetti/runline/releases/latest) for normal phone updates. Copy it to the phone and open it, or install over USB:

```sh
adb install -r /path/to/Runline.apk
```

Official builds retain the dedicated per-app signing key used since v0.2, so Android can update them in place and preserve recordings. A fresh clone builds with a development key; it cannot replace an official build with a different signature. Do not uninstall or clear storage without exporting important activities first. The v0.1 build used a different key and also requires an export before migration.

Local release signing is optional: `app/build.gradle.kts` reads an ignored `.signing/signing.properties` or `RUNLINE_STORE_FILE` / `RUNLINE_SIGNING_PASSWORD`. Without it, `assembleRelease` builds an unsigned APK. Keep keys and passwords outside Git. Pull requests never receive signing secrets; main-branch builds use repository secrets and upload signed APKs as private workflow artifacts.

A release is published only after source checks and the exact signed APK have been verified. [Release history](RELEASE-HISTORY.md) retains prior checksums and verification scope; [TESTING.md](TESTING.md) tracks device checks.
