# Optional AI and training plans

Runline keeps recording, local voice coaching and local training plans independent of an AI account. Anthropic is the first provider behind `CoachAiProvider`; no OpenAI connection is displayed or implemented yet.

## Setup and data

Open **Settings → AI**, choose Anthropic, enter an API key, then explicitly enable the module and any automatic features. Opening this sheet does not contact a provider. Review and plan-personalization actions are hidden while AI is disabled or no key is configured; local training plans remain available. The key is encrypted with AES-256-GCM using a non-exportable Android Keystore key. Ciphertext lives in `noBackupFilesDir`; neither the key nor ciphertext enters exports, app-instance Bundles, logs or the repository. The manifest disables backups and device transfer. This reduces extraction risk; it does not make an unlocked or compromised device immune to access.

The key is sent only to the fixed HTTPS origin `api.anthropic.com`; redirects are disabled. Requests include summaries, activity type, active/GPS-observed time, distance, pace, elevation estimates, splits and confidently detected lap/quarter comparisons. Saved activity effort and routine are labeled self-reported. **Include activity titles and notes** is a separate, off-by-default option; these values are treated as untrusted data. Raw coordinates and photos are never included. Map tile requests remain a separate existing feature.

Requests use the user's paid Anthropic API credits. Live advice, automatic completed-activity reviews and automatic plan changes have separate switches, all initially off. The non-generating **Test connection** checks access to both selected models (once if they are the same). It does not prove that a paid generation will succeed. Data/cost disclosure expands on demand; Save stays separate from testing.

Version 0.9 adds **All AI features** as a shortcut for live advice, automatic reviews and automatic plan changes. Selecting it also selects the AI module, subject to normal key/configuration validation on Save. It does not change note sharing or profile consent. Turning those three features off does not silently erase the configured key.

**Settings → Runner profile** offers optional experience, training preferences and health notes. The two text fields are bounded to 600 characters each. This self-report is stored atomically in app-private `noBackupFilesDir`, outside recording files and exports; it is not encrypted with the API-key mechanism. **Include profile in AI** defaults off and explicitly includes health notes when enabled. It does not enable the AI module. Saving or clearing the profile invalidates stale responses and clears the recent spoken-context memory. Unsaved profile text survives rotation only in memory, never in an instance-state Bundle; Close discards that draft. The clear action removes the saved profile. No age, health, symptoms or fitness level is inferred from GPS or pace.

Defaults verified against official docs on 2 November 2026: `claude-haiku-5-5` for live advice and `claude-sonnet-5-5` for review/plans. Model dropdowns show Haiku/Sonnet presets and a Custom model choice. An existing custom ID remains intact; the dropdown does not fetch a model list. Requests use Messages API `/v1/messages`, API version `2026-10-21` and Bearer authorization. API errors are mapped to local messages without echoing response bodies or credentials.

## Live advice and reviews

The recording service calls `AiRunCoach.update(run, gpsFresh)`. Live advice additionally requires ordinary voice coaching to be enabled. First advice waits the selected interval, then calls at most once every 3–10 active minutes, with no catch-up and a maximum of 24 attempts per activity. Shared context includes the plan's goal/date precision, today's intended session, next planned session, 14-day adherence, the active voice goal, up to eight recent activities and the recent GPS terrain estimate. Plans are intentions, not proof that the current workout followed them. Effort and optional profile details remain self-report; titles/notes still require their own switch.

Up to six local/AI cues from the last 15 minutes can accompany that context. Only matching Android TTS `onStart` events enter this bounded, memory-only history; generating or queueing a sentence does not count as speaking it. The prompt requests one short useful sentence, or `NONE` for silence, and tells the model to respect easy/recovery sessions, avoid repeated local cues and avoid quoting health notes aloud. These are instructions to the model, not guarantees of response quality.

Pausing, finishing, disabling or changing settings/profile invalidates pending speech. A response must arrive within 30 seconds, satisfy text bounds and retain matching request/context generations. Intervening speech invalidates an older AI response. One valid AI cue can wait in the same expiring priority slot used by local advice, without holding audio focus; goal completion and milestones take precedence. Routine speech leaves at least 85 seconds after the preceding utterance finishes. Network work uses an independent executor and bounded timeouts; recording never waits for it. See [COACHING.md](COACHING.md) for delivery priorities and expiry.

A post-run review is explicitly generated from the activity and recent activity summaries, or automatically when that option was enabled. Automatic failures do not loop or silently bill again; manual Generate review retries. Text and model/time are saved locally. Editing an activity does not silently regenerate a paid review; use Generate review for updated feedback.

## Training plans

Choose 5K, 10K, Half marathon (21.1 km), Personal progress or a custom distance. Distance goals use a native exact-day picker or target-month picker, within the existing 4–52-week horizon. Month goals retain month precision and use month end only as an internal schedule boundary. Personal progress has no race or distance target and creates a bounded eight-week schedule without a race taper.

Recorded Run history is read off the UI thread. A 7/28-day average/count stays visible even with sparse history; walks/hikes are excluded. At least four runs across three of the last four weeks supports a 28-day planning average. Otherwise the adjustable suggestion is a gentle 5 km/week over two days, explicitly separate from recorded fitness. Runs per week remains selectable, and starting-distance adjustment is optional. Distance plans offer easy/long sessions, periodic lighter weeks and a taper. It is a conservative scheduling template, not a guarantee of fitness, race readiness or injury prevention.

Version 2 plan storage records goal type and timing, including target month or rolling horizon. Version 1 plans retain their schedule, manual status and activity links when read and subsequently saved. Local rows show date, distance, kind and status without repeated advice; AI explanations/session notes expand under **Plan notes**.

**Personalize with Claude** sends the plan envelope and recent summaries. The response must contain every future planned session exactly once, with valid dates, distances and a small allowed set of routine types. It cannot move sessions, rewrite completed/skipped sessions, exceed the original local envelope, or increase an existing session by more than 10 percent. Missed sessions are not stacked later. Malformed, truncated, out-of-range or stale responses leave the current plan intact.

The plan links a saved Run to at most one same-day session when the run covers at least half its scheduled distance. This is a convenience match, not a claim the intended workout was fully completed; the user can change Planned/Done/Skipped manually. Older uncompleted sessions become Skipped. Optional automatic personalization makes at most one attempt per day when the app opens or an activity finishes. No background alarm or constant polling is installed; an app that stays closed reconciles on its next open. An existing goal edit or status change wins over an in-flight response.

## Verification scope

Version 0.9 profile, context and shared-speech behavior is implemented in source; integrated build/runtime verification is pending. The results below remain v0.8 evidence.

The integrated v0.8 build passed 158 JVM tests, including the new migration/baseline/timing coverage. Bounded API 34 checks confirmed local model choices save while AI is off, fixed sheet controls and keyboard-visible Save, month selection through rotation, Personal progress hiding date fields, and retention of the existing version 1 plan. The native disable regression also passed with a malformed draft key; the dummy credential was removed and made no requests. These checks made no real Anthropic generation request. [The current verification record](../README.md#version-08-verification) identifies completed checks and the remaining release/device work.

Provider tests use a fake transport for request structure, text-block parsing, invalid/truncated replies, key/rate/network errors and secret-free messages. Core tests cover scheduling caps, speech bounds, plan round-trip, dates, missed/completed matching and rejected unsafe plan edits. A real Anthropic key is not present in development, so live paid generation, account permissions and production model response quality remain for the owner to verify using the in-app connection test and explicit requests.

Primary references:

- [Anthropic models](https://platform.claude.com/docs/en/models/overview)
- [Create a Message](https://platform.claude.com/docs/en/api/messages/create)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
