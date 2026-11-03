# Goals for a run

Runline separates an optional longer-term aspiration from the goal you choose for an individual run. **Settings → AI → Overall goal** accepts up to 400 characters, for example “half marathon in April.” It does not create a calendar, require an exact race date or automatically increase future workouts.

## Choose and apply a goal

For a local goal, open **Voice coach**, enable guidance, choose the target and save. This requires no API account or network connection.

| Goal | Local behavior |
| --- | --- |
| No goal | Optional progress and other enabled feedback without a completion target. |
| Distance | Announces completion at the selected distance. |
| Pace | Gives bounded feedback around a chosen pace or pace range. |
| Time | Announces completion when the selected active duration is reached. |
| Distance within a time limit | Completes at the selected distance and compares active time with the limit. Pace guidance uses the target's required average, not a changing catch-up calculation. |

A pace range can accompany Distance or Time, and can replace the single target in Pace mode. Enter the faster pace first, such as 5:30–6:00 min/km. Feedback uses tolerances and cooldowns rather than reacting to every fix. Explicit pauses do not consume active duration. Time completion can be detected without a fresh GPS fix; distance and pace guidance still need suitable location data. Goal completion announces progress and does not finish the recording automatically.

For an AI suggestion, select **Run**, open **Smart goal**, then tap **Suggest a run**. Enable AI and configure a key first if requested. This is a paid request using the selected **Reviews and smart goals** model. It returns a short rationale and a concrete target, or a rest suggestion. The sheet shows a distance range separately from its proposed target where applicable.

Choose **Use goal** to save the concrete target and enable voice guidance, or **Adjust** to edit it and then Save. Closing the suggestion leaves the existing goal alone. A rest suggestion has no Use goal action and does not erase a previously saved target. The displayed distance range is guidance; applying the suggestion uses its stated target for local progress/completion. Optional pace bounds and easy/recovery/steady intent are preserved. The generated rationale is not stored in coaching preferences.

Manual settings are the local path when AI is off or unavailable. The local engine also calculates and validates the bounds for AI output; the Smart goal sheet does not silently substitute a generated local result for a failed paid request. After application, recording and ordinary speech work offline. Optional live AI remains controlled by its own switch and does not change the target during a run. The user can still deliberately edit coaching settings.

## What Smart goal considers

Finished Run activities provide distance, active duration, local date/start time, GPS ascent and optional saved effort. Edited activity type takes precedence; walks, hikes and unfinished/invalid recordings are excluded. The current calendar week's recorded distance, trailing seven-day volume, 28-day weekly average and longest recent run describe different windows; they are not interchangeable targets.

The pure engine computes distance and duration limits before requesting AI:

- Recent meaningful runs establish typical distance and the longest run in the preceding 30 days. A recording shorter than both 0.5 km and five active minutes does not establish a normal workout baseline, though its distance still counts toward recent recorded load.
- Four meaningful runs spread across at least three recent seven-day periods support an established-history calculation. Sparse history uses restrained limits rather than multiplying one run across a full week. No history yields short-duration limits without an assumed numerical pace.
- Remaining capacity uses a rolling seven-day load calculation. It is not a Monday-to-Sunday quota, a command to fill unused kilometres or debt from missed sessions.
- Recent high reported effort, prolonged or unusually hilly running, a training gap, or already running today can favor recovery/rest. If there is no useful distance capacity left, only rest is accepted. A low effort report is evidence of how that activity felt, not proof of general fitness.
- Single-session distance is bounded separately from total recent volume. A longer-term ambition cannot override either limit. Near-term increases depend on observed running and reported easy effort; there is no forecast that assumes future training will happen.
- Numerical pace bands require multiple recent runs reported as easy, with usable timing/distance and limited GPS ascent per kilometre. Missing evidence leaves pace unspecified. A time-only target has a tighter duration bound using observed faster pace so that assuming a slow pace cannot bypass distance capacity. A mixed distance/time target must imply a pace within the supported comfortable bounds and cannot also add a separate pace band.

The validator accepts only REST, DISTANCE, TIME or DISTANCE_TIME, and EASY, RECOVERY or STEADY effort. Distances must have a complete ordered range containing their target; numeric values must be finite and within their limits. Steady effort requires supporting history, and recovery-only conditions must remain recovery. REST has no numeric targets. Unknown workout-phase fields, invalid combinations, malformed output and unsupported pace targets are rejected.

The numerical cutoffs and formulas are app heuristics, not medically validated safety thresholds. GPS cannot measure fatigue, recovery, pain, heart rate or race readiness. Unrecorded training, cross-training, sleep, noisy elevation and incomplete effort reports limit the available evidence. Manual goals are user-entered settings and do not acquire a readiness guarantee from the Smart goal validator.

## Requests, privacy and updates

**Suggest a run** and **Suggest again** are explicit requests. Opening/reopening the sheet, rotating, editing effort or saving the overall goal never buys another suggestion automatically. One worker is allowed at a time, with no extra request queue or automatic retry. Existing manual settings remain usable after failure.

The temporary request/result survives Activity rotation in process memory. It disappears after process death, at a date change, or when relevant history/settings/consent change. A result is checked against the current data before it can be applied. If it was invalidated, request fresh advice explicitly. Recording does not wait for network work, and returning to a visible sheet only observes the existing request.

The overall goal is included when using AI, as disclosed in its field. Titles/notes and the runner profile have independent sharing controls; profile sharing also covers optional health notes. Raw route coordinates and photos are excluded. The model receives bounded summaries and the local numeric limits. See [AI.md](AI.md) for credentials, data handling and the separate live/review features.

An upgrade can migrate the old plan's concise goal and date precision into the freeform field once. It does not migrate its calendar or generated notes, rewrite the old file, or perform an AI request. Saving an empty goal records an explicit choice so the old aspiration cannot return on the next launch. The old automatic plan-update setting is ignored; no calendar worker runs after completion, effort edits or app opening.

## Evidence and verification

The implementation uses published observations to inform cautious product choices. These sources do not validate Runline's formulas or certify its suggestions:

- [England Athletics' explanation of training pace](https://www.englandathletics.org/news/simplifying-the-running-jargon-training-pace-2/) describes subjective effort and conversational easy running. Saved effort remains self-report rather than a physiological measurement.
- [Frandsen and colleagues' 2025 cohort study](https://pubmed.ncbi.nlm.nih.gov/40623829/) found an association between single-session distance spikes relative to the previous 30-day longest run and overuse injury. Considering session distance separately from total weekly volume is an inference from that evidence; the observational study does not establish a universally safe threshold.
- [Buist and colleagues' randomized trial](https://pubmed.ncbi.nlm.nih.gov/17940147/) found no injury-prevention benefit from its graded programme based on a weekly 10% rule compared with the standard programme. Runline does not present percentage progression as guaranteed protection.

The final v0.10 build passed 233 JVM tests with zero failures. Lint reported zero errors and 106 warnings; verification passed for 464 bilingual resources and 34 pinned icons. Native setup/rotation, injected loading/result/rest/error states and numeric target application passed. The exact signed update retained all 49 inspected original files and 22 activities; the temporary harness was cleaned up. GitHub CI/merge evidence is tracked on the release PR. Tests use synthetic history and fake AI transport; there has been no paid model generation or prospective field validation of Smart goal advice. Completed results belong in [TESTING.md](TESTING.md).
