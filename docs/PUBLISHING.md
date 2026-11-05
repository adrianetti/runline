# Distribution

Runline's source is published under [MIT](../LICENSE). Signed APKs are distributed through [GitHub Releases](https://github.com/adrianetti/runline/releases/latest). Install updates over the existing app; community builds use their own key and cannot replace the official installation without a deliberate signing arrangement.

## Open source

The publication review covers remote Git branches/tags, release asset names, PR discussions, committed screenshots and secret scanning. Development credentials, signing material, actual GPX files and native test backups stay outside the repository. Screenshots use synthetic recordings. No telemetry is automatically sent to contributors.

The source license does not relicense third-party libraries or imagery. Keep [third-party notices](../THIRD_PARTY_NOTICES.md), map attribution and provider terms. EOX global imagery has noncommercial restrictions; source publication is not permission for unrestricted imagery use. Resolve imagery rights or change providers before commercial distribution.

Use [Contributing](../CONTRIBUTING.md) and [Security](../SECURITY.md) when reporting issues. Diagnostics remain local until explicitly shared. Inspect even coordinate-redacted exports for private text/timestamps.

## Google Play

Deferred at the owner’s request on 5 November 2026. No Play submission or account enrollment is part of this release. The following is a readiness checklist to revisit when the developer account exists; verify policy requirements again then.

| Item | Current state / next action |
| --- | --- |
| Account | Register and verify a Play Console account. Google lists a one-time US$25 registration fee. [Account setup](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en). |
| Closed test | For applicable personal accounts created after 13 November 2023, arrange at least 12 opted-in testers continuously for 14 days, then apply for production access. This is separate from our GitHub checks. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en). |
| Target Android version | Current app targets API 34. New submissions now require API 36 or higher. Raise target SDK from 34 (compile SDK is already 37 after maintenance) and test edge-to-edge layout, foreground location, notifications, background jobs, permissions and process recovery on newer Android releases. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). |
| Native libraries | Verify both APK/AAB alignment and real 16 KB runtime behavior, including MapLibre rendering. A build success alone does not establish this. [16 KB guidance](https://developer.android.com/guide/practices/page-sizes). |
| Package/signing | Build an Android App Bundle and configure Play App Signing. Decide signing continuity before enrollment so existing APK users can update without losing access to their installation. Keep the existing private signing material protected. [App setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en). |
| Data and health declarations | Publish an accurate public privacy policy, complete Data safety and the Health apps declaration, and review foreground-location declarations. Describe local GPS recordings/logs, user-directed exports, map requests, optional Anthropic summaries, separately consented notes/profile data and local credential storage. Fitness recording is covered by the health-feature declaration. [Health declarations](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en). |
| Listing and support | Finalize name, developer identity/contact, short/full descriptions, content rating, support/privacy URLs and imagery rights. Use real app screenshots with synthetic data; never imply measured heart rate, perfect GPS, guaranteed coaching outcomes or features we do not ship. |
| Graphics | Prepare a 512×512 PNG app icon, 1024×500 feature graphic and phone screenshots according to current Play Console rules. The README gallery is an initial product presentation, not a submitted store listing. [Preview-asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). |

Run internal testing first, then the required closed-test track and production review. A Play listing can distribute an open-source app, and users would then receive updates through Google Play. Personal GitHub APK releases continue meanwhile.
