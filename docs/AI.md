# Optional AI coaching and smart goals

Runline records activities and runs its local voice coach without an AI account. Anthropic is the first provider behind `CoachAiProvider`; OpenAI is not yet implemented. The optional AI features are live advice, completed-activity reviews and an explicitly requested suggestion for the next run. There is no calendar planner or automatic plan-update feature.

## Setup and data

Open **Settings → AI**, choose Anthropic, enter an API key and enable the module. Models and the optional overall goal can be edited while the module is off. Opening settings does not contact a provider. **Test connection** explicitly checks access to the selected models, once if both selections are the same; it does not generate coaching or prove that a paid generation will succeed. Save is separate from testing.

The key is encrypted with AES-256-GCM using a non-exportable Android Keystore key. Ciphertext lives in `noBackupFilesDir`; neither plaintext nor ciphertext enters exports, app-instance Bundles, logs or the repository. The manifest disables backups and device transfer. This reduces extraction risk; it does not make an unlocked or compromised device immune to access. Credentials are sent only to the fixed HTTPS origin `api.anthropic.com`; redirects are disabled.

Requests spend the user's Anthropic API credits. **Live advice**, **Automatic reviews** and **Daily suggestion** have independent switches, initially off. **All AI features** controls all three and enables the module, subject to key validation on Save. Profile and title/note sharing remain separate. Daily suggestion authorizes one automatic preparation attempt per local day on an idle Run screen visit; explicit retry and manual reviews can incur additional requests. Opening Settings alone does not send a coaching request.

Activity summaries can include type, active/GPS-observed time, distance, pace, estimated ascent/descent, splits and confidently detected lap/quarter comparisons. Saved effort and routine are labeled self-reported. **Include activity titles and notes** is a separate, initially off option; supplied text is treated as data rather than instructions. Raw GPS coordinates and photos are never included. Map tile requests remain a separate feature.

**Overall goal · optional** is freeform text of up to 400 characters, such as “half marathon in April.” It is included when AI is used, as disclosed beside the field; no date or race-distance form is required. It is saved atomically in app-private `noBackupFilesDir`, outside activity exports. This text is not encrypted with the API-key mechanism. Unsaved text survives rotation in an Activity ViewModel, never an instance-state Bundle or autofill field. Saving an empty goal clears it. An existing legacy plan can supply its concise goal/date once, preserving month precision; the schedule and notes are not migrated or sent. The original plan file remains untouched, and an explicitly cleared goal is not resurrected. See [GOALS.md](GOALS.md).

**Settings → Runner profile** offers optional experience, training preferences and health notes. The two text fields are bounded to 600 characters each. The profile is stored atomically in app-private `noBackupFilesDir`, outside recording files and exports; it is not encrypted with the API-key mechanism. **Include profile in AI** defaults off and explicitly covers health notes when enabled. It does not enable AI. Unsaved profile text survives rotation only in memory; Close discards the draft. Clear removes the saved profile. No age, symptoms, health or fitness level is inferred from GPS or pace.

Saving or clearing the goal/profile invalidates stale AI responses and clears recent spoken-context memory. Settings/key changes also invalidate pending work. The model is instructed not to quote health notes aloud; this instruction is not a guarantee of model behavior.

The app's defaults are `claude-haiku-5-5` for live advice and `claude-sonnet-5-5` for reviews and Smart goals. Dropdowns offer preset and Custom model IDs without fetching a model list. Messages use `/v1/messages`, API version `2026-10-21` and Bearer authorization. Errors map to local messages without echoing response bodies or credentials. Model availability and account access still depend on Anthropic. See the [model catalog](https://platform.claude.com/docs/en/models/overview) and [Messages API](https://platform.claude.com/docs/en/api/messages/create).

## Live coach

The service supplies `AiRunCoach` with the applied voice-coach settings. The live coach additionally requires ordinary voice coaching to be enabled. Three parts share the work: `LiveCoachPlan` decides when to speak, `LivePoints` decides what about, and the model only gives that one observation a voice.

**When it speaks.** In active recording time:

- an opening line once 40 seconds have been recorded (skipped when a recording is resumed or recovered later than 2.5 minutes in);
- every local milestone as it happens: kilometre, repeated lap, halfway, final stretch, one kilometre left and goal reached. The live coach announces it in its own words instead of the built-in sentence. If it has not answered within 3.5 seconds, or is off, busy or failing, the built-in announcement is spoken as before;
- a check-in whenever the chosen quiet time (**Advice frequency**, 1–10 minutes, default 2) has passed since it last spoke, unless something was said in the last 20 seconds. Check-ins rotate through eight angles: pace trend, technique, recent history, morale, goal projection, effort check, split detail and the runner's aspiration. A real change of pace or a run that has just become the longest in 30 days goes first. In the last tenth of a goal only the finish is mentioned.

Check-ins are at least 20 seconds apart and everything stops at 80 requests per activity. A silent answer is retried after 45 seconds; a failed one after 30 seconds, doubling up to the quiet time. After two failures in a row the built-in coach speaks for itself again until a request succeeds. While the live coach is answering, built-in routine encouragement (settling in, steady, progress check-ins, hill support) is left to it; built-in pace-target and terrain cues are unchanged.

**What it says.** `LivePoints` works through the run summary and coaching context on the phone and picks one observation: this kilometre's time and what it shows (every kilometre faster, the fastest so far, slower by so much, the same as the last), a lap against the one before, whether the last two kilometres are fading or building, a real change of pace judged in context (on an easy day only genuine speeding is mentioned, and once; a late surge is cheered; coming back to the usual rhythm is good news), what is left and where the average lands, today against the previous run, the seven-day total, a run about to become the longest in 30 days, the months left to a dated goal, a form cue for this phase of the run, or an effort self-check. Each observation is offered once per activity and the same theme never twice running. After nothing worth saying, nothing is said.

Every figure is formatted on the phone the way it is spoken in the runner's language ("7 minutos 41 por kilómetro", "2,2 kilómetros", "20 segundos"). The model receives the observation in plain words, those figures, a coaching hint, the intended effort, the lines already spoken in this activity and the openings and words it has overused. It no longer receives the run summary, history, goal, profile or notes: those stay on the phone. No coordinates.

**What comes back.** One spoken line, usually one sentence of 8 to 20 words, hard limits 45 words and 300 characters. Before it may be spoken it is checked on the phone:

- every digit in it must be one of the figures it was given;
- it must be in the runner's language and addressed to the runner, not a note about the instructions;
- it may not ask the runner to tell it anything, nor address them with a gendered word;
- hill wording must pass the terrain-map direction check, which now fails any mention because observations never include terrain;
- it should not open like one of its last four lines or repeat four consecutive words of an earlier one.

A line that fails is asked for again once with the reason. A second failure is dropped (an echo alone is allowed through). Written paces that slip through ("7:41/km") are converted to spoken form.

**Speed.** With the live model `claude-haiku-5-5`: a milestone is requested with thinking disabled and `effort: medium`, median 1.1 s, 90th percentile 1.9 s, slowest 2.1 s over three replayed runs. A check-in, where a second or two does not matter, keeps thinking on at `effort: low`, median 2.0 s, 90th percentile 4.7 s, and produces fewer muddled lines. A custom model that rejects those options (HTTP 400) is asked again with a plain request. A check-in older than 15 seconds on arrival is discarded.

**Delivery.** Pausing, finishing, losing fresh GPS or changing settings/profile/goal invalidates pending speech. A check-in waits in the shared routine slot without holding audio focus, is spoken no sooner than 8 seconds after other speech ends, and expires after 40 seconds. A milestone voiced by the live coach keeps the milestone's own slot, priority, validity check and once-only rule. Built-in routine cues keep their 85-second quiet interval. Network work uses a separate executor; recording never waits for it. See [COACHING.md](COACHING.md) for priorities and expiry.

**Coach log.** Each request and its outcome are written to the activity's coach log (and so to **Export data** → `coach-log.json`) as `ai_requested`, `ai_ready` with the observation it voiced, `ai_silent`, `ai_failed` with the reason, or `ai_discarded`, with the latency. Prompts, answers that were not spoken and the key are never logged.

**Backtest.** `LiveCoachBacktest` replays recorded activities (a `points.csv` from **Export data**, or a GPX) through `CoachEngine`, the real `CoachSpeechSchedule`, `LiveCoachPlan` and the provider, writes what the runner would have heard, and has `claude-sonnet-5-5` grade every line against the run's data: accurate, clear, specific, useful, voice, fresh, and whether it asks for a reply, plus a 0–100 "coach feel" for the whole run. It is skipped unless `RUNLINE_BACKTEST_DIR` and `ANTHROPIC_API_KEY` are set, so CI never calls the provider. Run with only the built-in coach it reproduces the phone's real coach log of 10 November 2026 line for line, to the second.

Progress on three of the developer's runs (3 km with a time goal and history, 10 km with a distance goal, 4.7 km without a goal; the third was held out: only its scores were looked at). Coach feel is the mean of the three, two gradings each; grading noise is about ±3.

| Version | Coach feel | Line score (0–10) | Unclear or wrong lines | Lines asking for a reply |
|---|---|---|---|---|
| 0.21, built-in coach only (the live coach never got a word in) | 26 | 3.4 | 23% | 0 |
| 0.22 | 33 | 3.1 | 40% | 10 |
| 0.24 | 53 | 5.4 | 12% | 0 |

By run in 0.24: 61, 53 and 45 for the held-out one (27 in 0.21, 30 in 0.22). A second replay of almost the same code scored 54 (55, 56, 51): replays differ by a few points. An earlier round with Sonnet 5.5 as the live model, or with Haiku thinking on every line, scored within noise of the same round on Haiku as shipped, so the model is not what limits it. What the grader still faults: some praise is generic, a form cue or reassurance recurs on a long run, and the odd line claims how the runner feels.

## Reviews

A post-run review is explicitly generated from the activity and recent summaries, or automatically if **Automatic reviews** was enabled. Automatic failures do not loop or silently bill again; manual generation retries. Text, model and time are saved locally. Editing an activity does not silently regenerate a paid review. Reviews can suggest a next step; they do not create or update a calendar plan.

## Smart goal preparation

When Daily suggestion is enabled and configured, the first idle Record visit for Run loads today's saved result or prepares it using the selected reviews/Smart model. Context includes the overall goal, comparable recent runs, calendar-week/trailing-seven-day totals, effort and actual elapsed hours since the last completed run. The compact result presents distance or time, supported pace/range and effort; the user explicitly selects **Use**. Rest has no runnable target. No history-derived mileage, time, pace or weekly-capacity ceiling is calculated. See [GOALS.md](GOALS.md).

A synced private reservation precedes provider work, preventing automatic duplicate paid requests after rotation or process death. Validated results persist in no-backup storage. A failed/indeterminate attempt offers explicit retry, with a one-minute cooldown. History, goal, model and consent changes invalidate advice without silently making another paid attempt that day. Clock rollback and timezone changes cannot immediately create another automatic attempt. Existing diagnostic instrumentation records real usage, without request bodies or credentials.

At Start, a separate versioned session-intent file stores the selected goal and suggestion identity whether speech is on or off. Recovery, live coaching and later reviews use this snapshot; changing global settings does not rewrite it. Original GPS files stay untouched, and older recordings without a snapshot have unknown original intent.

Smart requests use Anthropic's [JSON structured output](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) with nine required fields and no additional properties. REST still has an explicit effort enum and no numeric target. This constrains response shape; the app separately validates finite/ordered values, compatible goal combinations, the same software input ranges as manual goals, rationale and current request state. It does not impose a training allowance or silently clamp a valid target. Refusal, truncation, unsupported models and invalid goals fail without an automatic paid retry. Sparse recordings are incomplete evidence, not a measured fitness ceiling. No history-derived pace bounds are imposed; numerical pace is optional and the model must ground it in usable pace/effort evidence.

Only one preparation request can run at a time; UI rendering never starts HTTP work. Preparation is skipped during active/recovered recordings. After a completed run or context edit, today's result is ineligible until explicitly refreshed; midnight allows a new automatic attempt. A late response cannot overwrite a session already started.

**Use** is the application action on Record. Manual adjustment remains in **… → Goal** and requires Save. The generated rationale stays with the private daily result, off the main screen and out of coaching preferences. An old result is never silently substituted for a failed request.

## Verification scope

The released v0.9 debug/release builds passed **197 JVM tests with zero failures**; lint reported zero errors and 117 warnings. Those are historical results, not verification of the new goal flow. [The v0.9 record](RELEASE-HISTORY.md#version-09-verification) retains its bounded runtime and update evidence.

The final v0.10 build passed **233 JVM tests with zero failures**; lint reported zero errors and 106 warnings. All 464 bilingual resources and 34 pinned icons passed verification. Tests cover freeform-goal migration/clear semantics, consent-bound context, explicit request gating, unsafe output rejection, stale responses, numeric goal validation and request setup failure recovery. Native setup/rotation, injected loading/result/rest/error states and numeric target application passed. The exact signed update retained all 49 inspected original files and 22 activities; the temporary harness was cleaned up. GitHub CI/merge evidence is tracked on the release PR. [TESTING.md](TESTING.md) is the current verification record.

Provider unit tests use fake transport. A separately authorized real-provider batch on 3 November 2026 exercised eight production requests with a key entered through a hidden local prompt and held only in memory for that initial batch. All HTTP requests succeeded, but the initial batch exposed two rejected Smart formats, an incorrect live pace comparison and unnecessary speech. Those failures motivated the v0.10.1 changes; follow-up evidence and its limits are recorded in [TESTING.md](TESTING.md). Private source recordings and raw provider reports are not committed. These bounded checks do not establish general coaching quality, Android audio behavior or prospective training safety. The storage design follows the [Android Keystore guidance](https://developer.android.com/privacy-and-security/keystore).

## Version 0.12 review and usage behavior

Automatic reviews are persisted with a 90-second delay after safe activity save, scheduled with Android JobScheduler and a network requirement. Pending work survives process death/reboot; an interrupted HTTP attempt is never silently retried. Notifications link to the saved review. Results use a validated four-field card of at most 100 words, with 75/200/200/260-character field limits. Saved cards and older text are readable with AI disabled. Current history is bounded as of the activity end, includes this run once in weekly totals and excludes it from comparison runs. Editing details while a request runs invalidates the result.

Provider calls record only bounded request metadata and reported usage; keys, prompts, response bodies, health notes and raw coordinates never enter usage logs. See [diagnostics](DIAGNOSTICS.md) for retention, export and list-price limitations.

Version 0.14 withholds raw GPS elevation from AI summaries. Available mapped height is identified by source and coverage; absent data is unknown, and partial coverage can undercount totals. Live hill claims require an eligible map estimate and pass an English/Spanish direction check. Terrain-map generation changes invalidate pending live advice. No new paid requests or retries are introduced.

Version 0.15 identifies the global or regional elevation model in summaries and live terrain context. Provider boundaries reset grade/height interpolation; raw GPS heights remain excluded from terrain advice.

## AI-managed runner profile (0.17)

The runner profile shows a local training snapshot from saved runs:
- runs in 28 days and km per week
- longest run in 90 days
- typical moving pace
- average self-rated effort

No model is called for it.

With AI ready and **Let AI keep my profile up to date** on, the review model reads the snapshot, coordinate-free summaries of up to 16 recent activities, the optional overall goal and its own previous assessment. It returns:
- an experience level
- at most two sentences of summary
- one focus

Health notes are sent only with the separate profile-sharing consent. Updates run after an automatic review, or on app open when a new run exists (at most every 6 hours), and on **Update now**. The assessment is stored separately from the self-report, so an update never invalidates a running review. Smart goals, live advice and reviews receive it as `ai_maintained_profile`, and the manual experience choice is hidden while it is on. **Clear** removes both.
