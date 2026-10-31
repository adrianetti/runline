# Strava sharing

Reviewed against official sources on 31 October 2026.

## Current path: save a file, then open Strava

Runline's **Share to Strava** action is a website handoff. On first use, a short explanation introduces the three steps:

1. Save the GPX file using Android's file picker.
2. Choose that file on Strava's website.
3. Review the activity and its visibility, then publish it.

After the file has been written successfully, Runline opens [Strava's file uploader](https://www.strava.com/upload/select). Later uses skip the introductory dialog. The recording stays in Runline, and the file remains wherever the user saved it. Opening the website does not transmit the file, connect an account, or publish a run.

Strava officially documents website uploads of GPX, FIT, and TCX workout files up to 25 MB. Its bulk-upload instructions explicitly include non-subscribers. [Upload guide](https://support.strava.com/en-us/articles/15402066-how-do-i-get-my-activities-to-strava), [bulk-upload limits](https://support.strava.com/en-us/articles/15402173-how-do-i-bulk-upload-activities-to-strava).

The official instructions describe a computer browser. A phone browser may need **Desktop site**, and its behavior must be tested on the user's phone. Android may route the URL to an installed handler; use a browser if Strava's native app does not expose the file uploader. No official public contract was found for importing a GPX activity into the Strava Android app through `ACTION_SEND`. Runline therefore does not target an undocumented native import intent or claim a one-tap upload. The saved file can also be uploaded from a computer.

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

The document-picker request code is `3102`. The helper also observes the activity lifecycle, so an export result waits until the current activity is resumed. A `ViewModel` retains an in-flight write across rotation; the worker uses application context and performs GPX encoding and document I/O away from the UI thread. Only the run ID is placed in the saved-instance bundle while the file picker is open. After process death during that picker, `loadRun` retrieves the saved recording again.

The helper rejects a run without a usable two-point segment and does not open the website after a canceled or failed save. It checks the 25 MB limit before writing. If no URL handler is available, it offers to copy the upload link. It never marks the activity as uploaded because it cannot observe what the user does on Strava's website.

A process death during the short export operation can interrupt the external file write. The source recording remains untouched; retry the export and remove any incomplete destination file. Opening an arbitrary document provider can also involve that provider's own network or account requirements. A local destination such as Downloads is the simplest choice for a subsequent browser upload.

## Why automatic upload is separate

Strava's current developer documentation requires a subscription to create a personal API application. New applications begin with the developer's own account and can self-upgrade to ten athletes. This requirement is different from manually uploading a file as a free Strava user. [API getting started](https://developers.strava.com/docs/getting-started/).

A supported automatic upload uses OAuth with the `activity:write` permission. The user can refuse scopes, so a connector must verify the returned grant. Access tokens expire, and refresh tokens can rotate. The exchange requires the app's client secret. [Authentication documentation](https://developers.strava.com/docs/authentication/).

The upload API accepts a multipart file and processes it asynchronously. A successful initial request is not the same as a published activity: the connector must inspect processing status, surface errors, and handle duplicate submissions. [Upload API](https://developers.strava.com/docs/uploads/).

No OAuth connector, backend, or automatic upload is implemented in the current sharing module. The app has no embedded Strava credentials.

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

Verified on the API 34 emulator: first-use explanation → Android file picker → successful save to Downloads → official uploader URL opened. The saved GPX parsed successfully and contained all 15 synthetic run samples in three segments. The remaining cases below are a test plan; browser sign-in, file selection and publication on Strava have not been verified.

- First-use dialog, cancel, and subsequent-use shortcut.
- No-route rejection and preservation of the saved run.
- File-picker cancellation, provider failure, and successful local save.
- Rotation while the picker is open and while a file is being written; exactly one website opening after success.
- Returning from a successful export while the app is backgrounded; no unsolicited background activity launch.
- Phone browser and desktop-site behavior, sign-in, file selection, and the final Strava review screen on the user's own account.
- Invalid browser handler and copy-link fallback.

The source GPX contains precise coordinates and timestamps. Confirm the destination activity's visibility before publishing. These checks are a test plan, not a claim that a live Strava upload has been verified.
