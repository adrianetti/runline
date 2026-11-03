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

Manual settings are the local path when AI is off or unavailable. The local validator checks AI response format and compatibility with manual goals; the Smart goal sheet does not silently substitute a generated local result for a failed paid request. After application, recording and ordinary speech work offline. Optional live AI remains controlled by its own switch and does not change the target during a run. The user can still deliberately edit coaching settings.

## What Smart goal considers

Finished Run activities provide distance, active duration, local date/start time, GPS ascent and optional saved effort. Edited activity type takes precedence; walks, hikes and unfinished/invalid recordings are excluded. The current calendar week's recorded distance, trailing seven-day volume, 28-day weekly average and longest recent run describe different windows; they are not interchangeable targets.

Claude chooses the next session from those observations and the optional overall goal. It receives individual recent run summaries, effort reports, the typical and longest recorded distances, days since the last run, and four separate seven-day windows with recording/effort counts. Empty periods mean no recordings are available, not proof that the runner did no training. Dividing sparse recordings across four weeks does not establish a normal weekly baseline.

There is **no history-derived distance cap, remaining weekly allowance, fixed percentage increase, automatic pace ceiling or compulsory recovery rule**. A suggested run can exceed the previous distance. The model is asked to choose meaningful, ambitious but realistic progression, explain it using the evidence, and consider recent hard effort, demanding sessions and gaps when easier running or rest makes sense. It must not simply copy the most ambitious stated distance or combine a large distance increase with a faster pace demand without support. Optional pace can be omitted in favour of an effort-led session when the data do not support a numerical target.

The local validator accepts REST, DISTANCE, TIME or DISTANCE_TIME, and EASY, RECOVERY or STEADY effort. It checks finite values, complete ordered ranges, valid combinations, and the same input support used by manual goals. These are software-format checks, not physiological limits or workload recommendations. A distance/time target must have a coherent representable implied pace and cannot also include a separate pace band. REST has no numeric targets. Unknown workout-phase fields and malformed output are rejected. Neither the parser nor the application step silently shortens a valid AI target.

A schema-valid suggestion is not proof of good coaching. GPS cannot measure fatigue, recovery, pain, heart rate or race readiness; unrecorded training, sleep, noisy elevation and missing effort limit the evidence. The model is instructed to respect those limits and preserve the user's ambition/timing. **Use goal** remains the explicit application step.

## Requests, privacy and updates

**Suggest a run** and **Suggest again** are explicit requests. Opening/reopening the sheet, rotating, editing effort or saving the overall goal never buys another suggestion automatically. One worker is allowed at a time, with no extra request queue or automatic retry. Existing manual settings remain usable after failure.

The temporary request/result survives Activity rotation in process memory. It disappears after process death, at a date change, or when relevant history/settings/consent change. A result is checked against the current data before it can be applied. If it was invalidated, request fresh advice explicitly. Recording does not wait for network work, and returning to a visible sheet only observes the existing request.

The overall goal is included when using AI, as disclosed in its field. Titles/notes and the runner profile have independent sharing controls; profile sharing also covers optional health notes. Raw route coordinates and photos are excluded. The model receives bounded summaries and the shared software input format; there is no precomputed training budget. See [AI.md](AI.md) for credentials, data handling and the separate live/review features.

An upgrade can migrate the old plan's concise goal and date precision into the freeform field once. It does not migrate its calendar or generated notes, rewrite the old file, or perform an AI request. Saving an empty goal records an explicit choice so the old aspiration cannot return on the next launch. The old automatic plan-update setting is ignored; no calendar worker runs after completion, effort edits or app opening.

## Verification

Regression tests cover a 5 km AI suggestion from a recorded 3 km run being accepted and applied without clamping, valid time/pace progressions, preserved history/effort, sparse and empty recorded weeks, and rejection of malformed or incompatible targets. Provider responses keep the strict nine-field JSON schema. Actual provider checks and their limits are recorded in [TESTING.md](TESTING.md); sampled outputs do not establish general coaching reliability or training readiness.
