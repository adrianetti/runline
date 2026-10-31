# Recording reliability: documented behavior and implementation requirements

Official-source review: 31 October 2026. This is a public behavior and Android architecture review. No Strava binary was decompiled, private endpoint inspected, or undocumented internal implementation assumed.

## What Strava documents

| Topic | Documented behavior | Implication for Runline |
| --- | --- | --- |
| GPS availability | Strava describes drift, signal loss, and bouncing points. It cannot reconstruct missing recorded GPS data. It recommends waiting for a good initial fix and reviewing device location and battery settings. | Show fix quality; retain valid samples durably; identify gaps rather than inventing a route. |
| Battery settings | Strava advises disabling restrictive power-saving or optimization settings when investigating Android recording problems. Its examples vary by manufacturer and include older menu names. | Offer relevant Android settings help, then verify the user's actual phone. A generic toggle is not proof of reliable background recording. |
| Recording and upload | Strava separates start/pause/finish from saving and upload. Recording can use GPS without mobile data when network-dependent features are unused. Moving time and GPS elevation can be recalculated server-side. | Make capture durable offline. Separate recording success from sharing success, and do not promise identical metrics merely from the same GPX. |
| Unsynced activities | Strava documents retrying an unsynced Android activity and exporting it as GPX for website upload. Low free storage can prevent processing or syncing. | Retain the local original when export/upload fails and surface storage problems. |

Sources: [Android GPS troubleshooting](https://support.strava.com/en-us/articles/15402062-troubleshooting-android-gps-issues), [recording activities](https://support.strava.com/en-us/articles/15402137-how-do-i-record-an-activity-on-strava), [sync troubleshooting](https://support.strava.com/en-us/articles/15402026-how-do-i-troubleshoot-syncing-issues).

The reviewed public documentation does **not** establish Strava's journal format, write frequency, crash-consistency guarantees, service restart strategy, or recovery guarantees after process termination, force-stop, reboot, or battery depletion. A saved but unsynced activity is not proof of how an unfinished recording survives those events. Claiming the same internal behavior or reliability would require evidence we do not have.

## Android boundaries

**A visible recording service is the appropriate mechanism for ongoing GPS capture.** Android 14 requires the location foreground-service type and its permission, together with runtime location access. Creating a location foreground service is subject to while-in-use restrictions; initiate the recording from the visible app. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#location).

**Ordinary process death, removing a task, and intentionally stopping an app are different events.** `START_STICKY` lets Android try to recreate a started service after its process dies; a recreated service can receive a null intent. `START_NOT_STICKY` does not request that restart. `onTaskRemoved` is a separate callback, with behavior affected by `stopWithTask`. These APIs do not promise a restart deadline or uninterrupted GPS samples. [Android Service reference](https://developer.android.com/reference/android/app/Service#START_STICKY).

**A user Stop is a stop.** On Android 13 and later, the foreground-service Task Manager can stop the entire app and remove its notification without delivering a callback. Android documents checking `ApplicationExitInfo.REASON_USER_REQUESTED` on a subsequent start. This must not be confused with an ordinary low-memory termination; do not promise continued recording after a deliberate stop. [User-initiated stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).

**A wake lock is not a universal guarantee.** Android documents that Doze can ignore wake locks and defer jobs and network access. Battery-optimization exemption is only partial, and vendor policies can add behavior that needs device testing. The system provides a battery-optimization settings action and a way to inspect the exemption state. [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Reliability requirements inferred for Runline

The following are engineering recommendations from those constraints, not descriptions of Strava internals or a statement that every item is already implemented:

- Persist accepted GPS samples as an append-only journal. Keep a small atomic session checkpoint with recording/paused state and accumulated active time. Flush accepted data independently of activity lifecycle callbacks.
- Tolerate a truncated final journal record after a crash. Preserve damaged files for diagnosis or recovery; do not silently replace an unreadable draft with a new recording.
- Persist enough state to distinguish an intended active recording from a paused session on ordinary service recreation. Restore foreground notification and capture only when the recovered state and Android permissions allow it.
- Use a monotonic clock for active timing. On recovery, preserve known time and mark the interruption; never add unobserved downtime as confirmed activity or connect GPS positions across an unknown gap.
- Make start, pause, resume, finish, and discard idempotent. Stop recording resources before completing save/discard, and commit the completed run before removing its recovery copy.
- Keep recording work off the UI thread. Bound memory growth and avoid rewriting the entire accumulated route for each location fix. Export/share work should not block GPS persistence.
- Treat revoked location permission, provider disablement, low storage, and failed writes as explicit states. Preserve existing data and provide a clear recovery action.
- Keep recording and sharing independent. A website, network, file-provider, or Strava failure must not delete the local run.
- Surface a concise recording-readiness check for precise location, notification visibility, and restrictive battery settings. Do not imply those checks certify future GPS continuity.

## Evidence needed before a reliability claim

Run deterministic persistence and state-machine tests, then verify the complete service on Android. Exercise ordinary process kill separately from task swipe, Task Manager Stop, Settings Force stop, permission revocation, location disablement, and reboot. For each case, compare the persisted samples, active duration, pause state, and recovered track segments; check for loss, duplication, fabricated bridge segments, and unintended recording restart.

On the actual phone, record screen-locked routes of at least 30 minutes, including stationary intervals, explicit pauses, weak-signal sections, and a longer run representative of normal use. Test normal and low-battery conditions with documented battery settings. Record gaps, battery consumption, storage growth, and recovery results. An emulator demonstrates control flow but cannot establish real GPS or manufacturer battery reliability.

A battery-dead or powered-off phone cannot collect new GPS points. The appropriate guarantee to pursue is preservation of previously committed data and an honest recoverable interruption—not a complete route across the powered-off interval. Any future comparison with Strava should use repeatable side-by-side field tests and report the phones, OS versions, settings, routes, and limitations rather than claim equivalence from architecture alone.

For the separate sharing path and current API requirements, see [STRAVA.md](STRAVA.md).
