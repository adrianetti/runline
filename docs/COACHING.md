# Optional spoken coaching

Voice coach is off by default. Enable it from Record before starting or from the recording **…** menu while an activity is underway. Settings persist; the recorder remains independent of voice availability. A suitable installed **offline** Android text-to-speech voice in English or Spanish is required. There is no cloud-voice fallback or generated character/persona.

Use **Test voice** and **Voice settings** to check the selected app language before a session. The app follows phone language by default with English fallback; explicit English/Spanish choices also govern coaching. Missing voice data, an unavailable engine or denied audio focus can prevent a prompt without stopping GPS recording. Preview feedback diagnoses the voice path, not future headphone or media-player behavior.

## Goals and announcements

| Goal | Behavior |
| --- | --- |
| Progress only | Optional kilometre announcements with average pace when available |
| Distance | Progress, one kilometre remaining for goals above one kilometre, and completion |
| Target pace | Sustained faster/slower-than-target and back-on-pace cues |
| Distance within a time | Distance completion plus comparison against the target’s average required pace |

Goals apply to total distance and **active recording time**, including standing still/GPS wait but excluding explicit pauses and unobserved process downtime. Changing/enabling a goal midway uses the existing activity as its baseline; it does not restart the activity or speak old milestones. Distance-with-time uses a fixed required average pace, not a changing projected finish or catch-up calculation.

Pace decisions use roughly 30 seconds of fresh movement. Guidance waits for warmup and a stable pace band, allowing a tolerance of the larger of 15 seconds per kilometre or 5% of target. Pace reminders are separated by at least two minutes. Kilometer/remain announcements are also spaced, with more relevant goal cues prioritized. These are GPS estimates; weak/stale locations, insufficient movement, gaps and pauses suppress unreliable reminders.

The service owns coaching, so closing the screen does not intentionally cancel an ongoing session’s coach. Pause, finish and interruption stop speech. Recovery or configuration changes baseline existing history and do not replay a backlog. If voice output is busy, stale pace/kilometre cues are not queued; at most a short-lived goal-completion cue may wait. Denied focus or an engine failure can still omit a cue. Goals must not be treated as a guaranteed alarm.

## Music, calls and headphones

Runline uses Android text-to-speech with speech/navigation audio attributes and requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for each short utterance. This asks compatible players to temporarily lower music. Focus is released after completion, errors, focus loss, pause/finish or a 15-second speech watchdog. Engine initialization has its own bounded timeout. The app does not directly change global media volume, control Spotify, or register as a music player.

Actual ducking or pausing follows Android, the other app, the TTS engine and the current audio route. Calls/spoken audio can interrupt or deny a prompt. Wired/Bluetooth routing, media resume behavior and Spotify on the user’s Samsung require real-device checks; they have not been verified. Stopping speech must not restart another app’s audio on its behalf.

Official API behavior is described in [Android audio focus](https://developer.android.com/media/optimize/audio-focus), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech) and [Voice](https://developer.android.com/reference/android/speech/tts/Voice). These platform contracts are not evidence of a particular player/device test.

## Implementation and verification

`CoachConfig` validates settings; `CoachEngine` makes incremental GPS/timer decisions without changing recorded samples. `VoiceCoachSettings` stores optional preferences, `CoachSpeech` wraps TTS/focus and `SpeechLease` manages focus/speech lifetime. `TrackingService` handles runtime ownership; `VoiceCoachSheet` exposes settings and preview.

Pure tests cover decision boundaries, pause/recovery/configuration behavior and focus lifecycle, but cannot prove audible output or recipient ducking. Final build/runtime results are tracked in [TESTING.md](TESTING.md) and [README.md](../README.md#version-06-verification). Physical Spotify/headphone/Samsung and field GPS behavior remain unverified unless explicitly recorded there.
