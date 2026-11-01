# Strava sharing

Reviewed against live official sources on 1 November 2026.

**Decision after the direct-sync audit:** keep the free manual GPX handoff. The user declined a subscription and asked to retain this path if a supported free connection for Runline could not be established. No documented, reliable automatic-upload path was found for our own recorder without registering an integration. This does not mean every existing watch or app integration requires its users to subscribe.

## Current path: save automatically, then open the uploader

Runline's **Share to Strava** action prepares a GPX and opens the website uploader. On Android 10 and later it saves directly to **Downloads**, without a storage permission or file-save dialog. The first-use explanation says what will happen; subsequent uses go directly to saving. On Strava:

1. Choose the saved GPX from Downloads.
2. Review the activity and its visibility, then publish it.

Android 8/9 uses Android's file picker to select a destination first. If an automatic Downloads save fails, Runline also offers that picker. A persistent message in Runline shows the actual saved filename and lets the user copy it. A repeated export may create a second file with a suffix; the displayed name is read back from the document provider.

Only after a successful save does Runline open [Strava's file uploader](https://www.strava.com/upload/select). It selects a generic HTTPS browser handler, rather than allowing the installed Strava app to intercept the uploader link and potentially open an unrelated screen. If there is no default browser, Runline offers the available browsers. If no browser can open, it leaves the file saved and offers to copy the uploader URL.

Opening the website does not transmit the file, connect an account or publish a run. The recording stays in Runline. Strava officially documents website uploads of GPX, FIT and TCX workout files up to 25 MB, including manual uploads by non-subscribers. [Upload guide](https://support.strava.com/en-us/articles/15402066-how-do-i-get-my-activities-to-strava), [bulk-upload limits](https://support.strava.com/en-us/articles/15402173-how-do-i-bulk-upload-activities-to-strava).

The official instructions describe a computer browser. A phone browser may need **Desktop site**, and its final upload behavior still needs testing on the user's phone. The saved file can also be uploaded from a computer.

## What the installed Strava app can do

Strava documents an Android app-to-app **authorization** handoff: an `ACTION_VIEW` intent to `https://www.strava.com/oauth/mobile/authorize` can open the installed Strava app to ask for consent. That endpoint needs a registered client ID and callback; completing OAuth still requires a confidential client-secret exchange and API upload. Installing Strava alone does not provide those application credentials. [Mobile OAuth documentation](https://developers.strava.com/docs/authentication/#requestingaccess).

No official public contract was found for creating a recorded activity by sending GPX to Strava's Android app with `ACTION_SEND`. Strava's Suunto instructions explicitly direct manual activity-file uploads to the website. A social post/share action or importing a route for navigation is not the same as uploading a completed workout. Runline does not use an undocumented intent, reuse another app's API credentials or treat opening Strava as successful upload. [Official file-import instructions](https://support.strava.com/en-us/articles/15401781-suunto-app-and-strava).

Watch companion apps use their own existing integrations. For example, Mi Fitness asks the user to authorize Mi Fitness to access Strava; this does not register Runline or grant it the companion app's credentials. [Mi Fitness connection guide](https://support.strava.com/en-us/articles/15401633-mi-fitness-and-strava).

## Health-platform alternatives checked on 1 November 2026

- **Health Connect:** Strava documents sending activity time, distance and calories to Health Connect, and receiving weight. It does not document importing completed activities from Health Connect, so writing a Runline recording there would not establish Strava sync. [Strava's Health Connect guide](https://support.strava.com/en-us/articles/15401554-health-connect-and-strava).
- **Samsung Health:** it can send GPS activities to Strava and exchange some exercise data with Health Connect. However, the reviewed documentation does not establish that GPS routes written by Runline through Health Connect would be imported and forwarded to Strava. This remains unverified, not a promised workaround. Direct writes through Samsung's Health Data SDK require partner approval. [Samsung–Strava integration](https://support.strava.com/en-us/articles/15401747-samsung-health-and-strava), [Samsung's Health Connect data support](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect), [Samsung SDK registration process](https://developer.samsung.com/health/data/process.html).

## Module contract

`app/src/main/java/com/runline/app/sharing/StravaSharing.kt` owns the website handoff:

```kotlin
val stravaSharing = StravaSharing(this, { id -> store.get(id) }, ::notice)
stravaSharing.restoreState(savedInstanceState)

// From the saved run's Share to Strava control:
stravaSharing.start(run)

// Before forwarding an unrelated result to other handlers:
if (stravaSharing.handleActivityResult(requestCode, resultCode, data)) return

// Activity state / lifecycle integration:
stravaSharing.saveState(outState)
stravaSharing.onResume()
stravaSharing.close()
```

The fallback document-picker request code is `3102`. Android 10+ uses `MediaStore.Downloads`: create the row with `IS_PENDING=1`, write and close the GPX, then clear pending status so it is visible to the browser. Failed writes delete the new row before offering the fallback picker. No broad storage permission is requested. [Android shared-storage documentation](https://developer.android.com/training/data-storage/shared/media).

Browser discovery needs the manifest query for `ACTION_VIEW`, `CATEGORY_BROWSABLE` and the HTTPS scheme. The probe URL is used only to resolve installed handlers; Runline does not request that page. The real uploader is then opened with an explicit browser package.

The helper also observes the activity lifecycle, so an export result waits until the current activity is resumed. A `ViewModel` retains an in-flight write across rotation; the worker uses application context and performs GPX encoding and document I/O away from the UI thread. Only the run ID is placed in the saved-instance bundle while the file picker is open. After process death during that picker, `loadRun` retrieves the saved recording again.

The helper rejects a run without a usable two-point segment and does not open the website after a canceled or failed save. It checks the 25 MB limit before writing. If no URL handler is available, it offers to copy the upload link. It never marks the activity as uploaded because it cannot observe what the user does on Strava's website.

A process death during export can interrupt the write. A pending MediaStore item stays hidden until the provider expires it; the app cannot guarantee cleanup after being killed. With a manually chosen document provider, an incomplete destination file may be visible and need removal. The source recording remains untouched, so sharing can be retried. Opening an arbitrary document provider can also involve that provider's own network or account requirements. A local destination such as Downloads is the simplest choice for a subsequent browser upload.

## Why automatic upload is separate

Strava's current developer documentation requires a subscription to create a personal API application. New applications begin with the developer's own account and can self-upgrade to ten athletes. This registration requirement is different from manually uploading a file as a free Strava user or using an already registered integration. [API getting started](https://developers.strava.com/docs/getting-started/).

A supported automatic upload uses OAuth with the `activity:write` permission. The user can refuse scopes, so a connector must verify the returned grant. Access tokens expire, and refresh tokens can rotate. The exchange requires the app's client secret. [Authentication documentation](https://developers.strava.com/docs/authentication/).

The upload API accepts a multipart file and processes it asynchronously. A successful initial request is not the same as a published activity: the connector must inspect processing status, surface errors, and handle duplicate submissions. [Upload API](https://developers.strava.com/docs/uploads/).

No OAuth connector, backend, or automatic upload is implemented in the current sharing module. The app has no embedded Strava credentials. The simplified Downloads/browser handoff uses no Strava account tokens.

## If automatic upload is chosen later

Use a small private HTTPS backend for this athlete rather than putting the client secret in the APK. The proposed flow is:

1. Authenticate the phone to the private backend using a revocable device credential.
2. Start OAuth with a random, expiring, single-use state bound to that device and a fixed registered redirect URI. Request only the permissions needed for the chosen feature.
3. Exchange the authorization code on the backend. Keep the Strava client secret and tokens in server-side secret storage; serialize token refresh so a rotated refresh token is not lost.
4. Upload the user's chosen local GPX over HTTPS after an explicit share action. Enforce file size and format checks, and associate a stable local run ID with the upload attempt.
5. Persist the Strava upload ID, poll with bounded backoff, and save the resulting activity ID only after processing succeeds. Keep a retryable failure state and reconcile an ambiguous network timeout before re-uploading.
6. Expose disconnect and revoke handling. Keep logs free of tokens and route contents, and avoid retaining a second permanent route archive on the backend.

These are proposed implementation choices, not claims about existing functionality. Review the current [API agreement](https://www.strava.com/legal/api) and [API policy](https://www.strava.com/legal/api_policy) before enabling the integration; automatic upload should not silently turn the app into an API-derived analytics or AI service.

External inputs still needed: the user's decision to use a subscription-backed developer integration; a registered Strava client ID; secure provisioning of its client secret; an owned HTTPS backend and registered callback domain; and an actual user OAuth grant. Do not request a client secret in chat or commit it to the repository. No account connection is implied by selecting the current website-sharing action.

## Handoff checks

Verified for v0.3 on the API 34 emulator on 1 November 2026: the introductory dialog and cancellation, automatic Downloads save without a file picker, a valid GPX containing all 121 synthetic samples in two segments, and opening the official uploader URL in a browser. No activity was uploaded. The cases below remain a broader test plan; Android 8/9, failure injection for MediaStore, multiple-browser handling, actual Strava sign-in, file selection and publication have not been verified. The user explicitly chose subscription-free sharing.

- First-use dialog, cancel, subsequent-use shortcut and actual Downloads filename.
- Android 10+ automatic save, pending-row publication and rollback on failure.
- Android 8/9 picker fallback and failed automatic-save fallback.
- Default browser, multi-browser chooser and absence of a qualified browser; installed Strava must not intercept the uploader URL.
- No-route rejection and preservation of the saved run.
- File-picker cancellation, provider failure, and successful local save.
- Rotation while the picker is open and while a file is being written; exactly one website opening after success.
- Returning from a successful export while the app is backgrounded; no unsolicited background activity launch.
- Phone browser and desktop-site behavior, sign-in, file selection, and the final Strava review screen on the user's own account.
- Invalid browser handler and copy-link fallback.

The source GPX contains precise coordinates and timestamps. Confirm the destination activity's visibility before publishing. These checks are a test plan, not a claim that a live Strava upload has been verified.
