# Open-source and Google Play readiness

Prepared 4 November 2026. This is a distribution plan, not a claim that Runline is published or Play-ready. The repository remains private. Version 0.12 continues the existing signed APK channel.

## Open source

The app can become an open-source project. Before changing visibility:

- Choose a source license. MIT is a simple permissive candidate; GPLv3 is a candidate if distributed derivatives should retain source-sharing obligations. No license is selected or granted by this plan. Review the actual [MIT](https://opensource.org/license/mit) and [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) texts before choosing.
- Review the complete Git history, release assets, screenshots and documentation for personal routes, profile/health information, private examples and credentials. Keep the existing secret scan. The owner-only development key and app signing key remain outside source, CI logs and screenshots.
- Confirm third-party licenses and map-provider terms. Tabler and MapLibre have attribution/license requirements; the global EOX imagery has noncommercial restrictions. A free app is not, by itself, proof that every use qualifies. See [MAPS.md](MAPS.md) and [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Resolve permission or replace imagery before broader/commercial distribution.
- Keep issue templates and reproduction bundles free of private coordinates by default. Diagnostics are local; developers receive a log only when a user explicitly shares it. Do not add automatic public telemetry or upload real runs to issue trackers.
- Document a clean build without personal signing material. Community builds use their own key and cannot replace the owner's signed installation unless signing continuity is deliberately arranged.

Opening the source and publishing in Google Play are independent choices. Public visibility should be a deliberate final step after that review.

## Google Play

Publication is feasible, but requires a release-preparation pass and a developer account.

| Item | Current state / next action |
| --- | --- |
| Account | Register and verify a Play Console account. Google lists a one-time US$25 registration fee. [Account setup](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en). |
| Closed test | For applicable personal accounts created after 13 November 2023, arrange at least 12 opted-in testers continuously for 14 days, then apply for production access. This is separate from our GitHub checks. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en). |
| Target Android version | Current app targets API 34. New submissions now require API 36 or higher. Upgrade compile/target SDK and test edge-to-edge layout, foreground location, notifications, background jobs, permissions and process recovery on newer Android releases. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). |
| Native libraries | Verify both APK/AAB alignment and real 16 KB runtime behavior, including MapLibre rendering. A build success alone does not establish this. [16 KB guidance](https://developer.android.com/guide/practices/page-sizes). |
| Package/signing | Build an Android App Bundle and configure Play App Signing. Decide signing continuity before enrollment so existing APK users can update without losing access to their installation. Keep the existing private signing material protected. [App setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en). |
| Data and health declarations | Publish an accurate public privacy policy, complete Data safety and the Health apps declaration, and review foreground-location declarations. Describe local GPS recordings/logs, user-directed exports, map requests, optional Anthropic summaries, separately consented notes/profile data and local credential storage. Fitness recording is covered by the health-feature declaration. [Health declarations](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en). |
| Listing and support | Finalize name, developer identity/contact, short/full descriptions, content rating, support/privacy URLs and imagery rights. Use real app screenshots with synthetic data; never imply measured heart rate, perfect GPS, guaranteed coaching outcomes or features we do not ship. |
| Graphics | Prepare a 512×512 PNG app icon, 1024×500 feature graphic and phone screenshots according to current Play Console rules. The README gallery is an initial product presentation, not a submitted store listing. [Preview-asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). |

Run internal testing first, then the required closed-test track and production review. A Play listing can distribute an open-source app, and users would then receive updates through Google Play. Personal GitHub APK releases continue meanwhile.
