# Optional spoken coaching

Voice coach is off by default. Enable it from Record before starting or from the recording **…** menu while an activity is underway. Settings persist; the recorder remains independent of voice availability. A suitable installed **offline** Android text-to-speech voice in English or Spanish is required. The optional Anthropic module writes advice, but the phone's installed voice still speaks it; there is no cloud-voice fallback.

Use **Test voice** and **Voice settings** to check the selected app language before a session. Phone language is the default with English fallback; explicit English/Spanish choices also govern coaching. Missing voice data, an unavailable engine or denied focus can omit a prompt without stopping GPS capture.

## Goals and optional feedback

| Setting | Behavior |
| --- | --- |
| Progress only | Kilometre updates and enabled contextual feedback |
| Distance | Progress, one kilometre remaining for goals above one kilometre, and completion |
| Target pace | Sustained faster/slower-than-target and back-on-pace cues |
| Distance within a time | Distance completion and comparison with the target's required average pace |
| Pace guidance | Can be disabled independently of the goal |
| Encouragement | Brief settling-in, sustained-effort, halfway and final-stretch cues |
| Climbs and descents | Cautious GPS-height context; no target-speed pressure on detected climbs or descents |
| Laps and comparisons | Repeated-loop and quarter-of-loop comparisons when GPS evidence is sufficient |

The four feedback modules default on **inside the existing voice-coach opt-in**. Kilometre announcements retain their separate switch. New users do not get unsolicited speech. The settings sheet keeps draft fields/toggles through recreation and permits turning guidance off even with an invalid goal draft.

Goals use total accepted GPS distance and **active recording time**, including standing still/GPS wait but excluding explicit pauses and unobserved process downtime. Enabling/changing a goal midway baselines the existing session rather than restarting it or speaking old milestones. Distance-with-time uses a fixed required average pace, not a changing catch-up calculation.

Pace decisions use approximately 40 seconds of fresh movement, a 60-second warmup and 25 seconds of sustained evidence. Leaving the target band requires the larger of 18 seconds/km or 6%; returning uses a narrower band of 10 seconds/km or 3.5%. Changed pace feedback waits at least 150 seconds after the previous pace cue; repeating the same off-target condition waits five minutes. The shared speech scheduler can delay or omit it further. This hysteresis reduces repeated faster/slower reversals around a target such as 5:30/km. Insufficient movement, implausible jumps, weak/stale fixes, gaps and pauses suppress unreliable advice.

Encouragement is spaced at least three minutes apart and yields to useful goal/pace information. Localized phrase alternatives use shuffle bags to avoid immediate repetition, including a recovery cue after a detected hill. Milestone and lap templates add measured context. This is a bounded rule-based vocabulary, not a claim of hundreds of independently validated situations.

The map, local coach and AI context share `TerrainTrend`: a bounded regression of GPS height over a recent 35–55 m moving window, with quality, residual-noise and recent-movement gates. Missing heights, poor fixes, gaps and stops remain unknown, not flat. Entering a climb/descent requires an estimated ±3% slope; returning toward flat uses ±1.5% hysteresis. Detected climbs/descents suppress pace correction, followed by two minutes of recovery before pace correction can resume, even if terrain announcements are disabled. This describes GPS height, not mapped terrain or measured effort. The app does not know pain, heart rate, footing, fatigue or weather unless the user separately supplies notes, and must not tell someone to ignore them.

## Estimated laps and sections

`LapTracker` searches for return points with matching travel direction after at least 300 metres and 90 seconds. A second loop must have similar distance and agree spatially at sampled positions before either loop is reported. Gaps, pauses, poor accuracy, implausible movement and changed shapes interrupt matching. These conservative requirements can miss short loops or incomplete routes; an absent lap is not proof that the user did not run one.

Verified loops have distance, observed duration, estimated ascent/descent and four equal-distance sections. The comparison uses the previous matched loop; a section is highlighted only when its pace is at least 8% slower. A longer section time does not establish a cause or a lack of effort. Height changes can add context, but there are no named parks, official lap timing or terrain-corrected grades. Saved activity details and the optional AI summaries can use the same local analysis.

## Optional Anthropic advice

AI is configured separately and defaults off. Live advice requires both voice guidance and the AI live switch, a saved personal API key and network access. Calls use bounded independent workers and shared goal/plan/history/terrain context; they never run on the recorder's persistence path. Optional profile and activity-note sharing have separate consent switches. The model can return silence. A fresh, short response may be spoken only while recording remains active and its settings/profile/request generations still match. Intervening speech, stale GPS and priority goal completion can invalidate it. [AI.md](AI.md) documents models, billing, data boundaries, plan adaptation and offline behavior.

## Shared speech timing

`CoachSpeechSchedule` keeps at most three pending slots: goal completion, the newest milestone, and one routine cue. It is not a FIFO backlog. Priority is goal, milestone, terrain, AI, pace, then encouragement. Goal completion clears lower-priority pending work; a milestone clears older routine advice when delivered. Duplicate kilometre/goal announcements are suppressed.

Quiet intervals begin when speech finishes, fails or stops: two seconds for a goal, 30 seconds for a milestone and 85 seconds for routine feedback. Waiting never holds audio focus. Pending work expires: 90 seconds for a goal, 60 for milestones/terrain/encouragement, 75 for AI and 25 for pace, with validity rechecked against the current situation. Expiry can omit a low-priority cue rather than deliver stale instructions. Only a matching TTS start enters the short, memory-only local/AI speech history; that history can help an opted-in AI request avoid repeating what was actually spoken.

## Music, calls and lifecycle

Runline requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with speech/navigation attributes for each short utterance. Compatible players may lower music. Focus is released after completion, errors, focus loss, pause/finish or a 15-second speech watchdog. Initialization has its own bounded timeout. Runline never sets global media volume, controls Spotify, impersonates a music player or restarts another app's audio.

The service owns live coaching. Closing the screen does not intentionally stop it. Pause/finish/interruption stops speech and invalidates pending work. Configuration changes/recovery baseline history; old pace or kilometre cues are not replayed. Current cues can wait only in the bounded, expiring slots above. Prompts are helpful feedback, not a guaranteed alarm.

On 2 November 2026 the user reported that the v0.6 coach worked well during their run. This is useful field feedback, not a controlled audit of Samsung/Spotify/headphone volume or every audio route. Exact versioned evidence remains in [TESTING.md](TESTING.md) and [Release history](RELEASE-HISTORY.md).

Official contracts: [Android audio focus](https://developer.android.com/media/optimize/audio-focus), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech) and [Voice](https://developer.android.com/reference/android/speech/tts/Voice).
