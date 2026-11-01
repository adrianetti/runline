# Strava research archive

**Historical only.** Runline 0.5 removes the Strava button, website handoff and `StravaSharing` module at the user's request. Current behavior is [Export GPX](EXPORTS.md): save a file locally, with no browser launch, account connection or automatic upload. This document retains the prior decision and source record; it is not a current integration guide or roadmap.

## Previous implementation: versions 0.2–0.4

The old action saved GPX to Downloads on Android 10+, or used a document picker on Android 8/9 and after an automatic-save failure, then opened Strava's website uploader in a browser. The user still had to select the file, review visibility and publish. Opening the website never transmitted the file or established an account connection. No OAuth client, backend, account tokens or automatic upload was implemented.

Historical v0.3 API 34 checks on 1 November 2026 covered the introductory dialog/cancellation, automatic Downloads saving without a picker, a valid GPX with 121 synthetic samples in two segments, and opening the official browser uploader. No Strava account login, upload or publication was tested. The v0.4 implementation was unchanged. Android 8/9 picker runtime and the user's actual phone-browser upload flow were not verified.

## Official-source findings recorded on 1 November 2026

These findings were recorded during the earlier integration audit; they have not been re-audited as part of removing the feature. External product rules can change.

- Strava documented manual website uploads of GPX, FIT and TCX files up to 25 MB, including for non-subscribers. The documented flow used a computer browser; a phone might need Desktop site. [Upload guide](https://support.strava.com/en-us/articles/15402066-how-do-i-get-my-activities-to-strava), [bulk limits](https://support.strava.com/en-us/articles/15402173-how-do-i-bulk-upload-activities-to-strava).
- Android's app-to-app OAuth handoff required a registered client ID and callback. It authorized a registered integration; installing Strava alone did not supply Runline with credentials. No documented public GPX `ACTION_SEND` contract for creating a completed activity was found. [Mobile OAuth](https://developers.strava.com/docs/authentication/#requestingaccess), [official manual file-import instructions](https://support.strava.com/en-us/articles/15401781-suunto-app-and-strava).
- Existing watch companion apps used their own registered integrations. Authorizing Mi Fitness did not register Runline or grant it that integration's credentials. [Mi Fitness connection guide](https://support.strava.com/en-us/articles/15401633-mi-fitness-and-strava).
- The developer getting-started page required a subscription to create a personal API application. This differed from manual free-user uploads or use of an already registered integration. [API getting started](https://developers.strava.com/docs/getting-started/).
- A supported automatic uploader required OAuth `activity:write`, server-side confidential credentials, token-refresh handling and asynchronous upload-status reconciliation. These were never built. [Authentication](https://developers.strava.com/docs/authentication/), [upload API](https://developers.strava.com/docs/uploads/), [API agreement](https://www.strava.com/legal/api), [API policy](https://www.strava.com/legal/api_policy).
- Strava's Health Connect guide documented outgoing time/distance/calories and incoming weight, not importing completed workouts. Writing a recording into Health Connect therefore did not establish Strava sync. [Health Connect guide](https://support.strava.com/en-us/articles/15401554-health-connect-and-strava).
- Samsung Health documented Strava integration and some Health Connect exchange, but the reviewed sources did not establish that a third-party GPS route written through Health Connect would be forwarded to Strava. Direct Samsung Health Data SDK writes required partner approval. No workaround was implemented or promised. [Samsung–Strava](https://support.strava.com/en-us/articles/15401747-samsung-health-and-strava), [Health Connect support](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect), [SDK registration](https://developer.samsung.com/health/data/process.html).

The user initially retained the free manual handoff, then chose to remove the Strava action entirely in v0.5. No credentials, subscription, backend or account connection are needed for the current file export. A saved GPX remains portable, and the user controls its eventual destination.
