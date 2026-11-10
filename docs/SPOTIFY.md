# Music and Spotify

Music control is optional and off by default. Runline records the same with or without it, and a music problem never reaches the recorder.

## What it does

Open **Settings → Music**.

- **Control music** is the main switch.
- **Start with the run** and **Pause with the run** work with any player. Without a Spotify connection Runline sends the play and pause keys a headset sends. It resumes only music it paused itself, and it starts music only when nothing is playing.
- With Spotify connected, Runline starts one of your playlists on the phone, shows the track on the recording screen, and can change playlist as the run changes.

The recording screen gets a strip above Pause: cover, title and artist, play or pause, and next. Tap the title to open the track in Spotify. Without a Spotify connection the strip shows only the two buttons.

## Connect Spotify with your own Client ID

Spotify limits a Development Mode app to the accounts its owner adds by hand (five per app) and forbids sharing an app's credentials. So Runline ships no Spotify credentials. Each person connects through a free Spotify app of their own. Spotify asks the app's owner for Premium, and its playback control works only for Premium accounts.

1. Open the [Spotify developer dashboard](https://developer.spotify.com/dashboard) and create an app. Any name and description will do.
2. Add the redirect URI `runline://spotify-callback`.
3. Tick **Web API** and save.
4. Copy the **Client ID** into **Settings → Music** and tap **Connect**. Sign in on the Spotify page that opens; it sends you back to Runline.

The **Client secret** is never needed. Runline uses Authorization Code with PKCE, so there is nothing secret in the app, in the repository or in a build. The Android package name and signing fingerprint are not needed either, so a fork or a self-built APK connects the same way.

To let someone else use your Spotify app instead of making their own, add their account under **User Management** in the dashboard. An account that is not listed gets "Add this account under User Management".

## How the music follows the run

Choose a playlist for **Calm**, **Steady** and **Push**. One is enough to start: a mood without a playlist borrows its neighbour's. With **Match the music to the run** on, Runline picks the mood from facts the recording already has:

| Situation | Effect |
| --- | --- |
| Run with no special intent | Steady |
| Easy or recovery intent, walks and hikes | Calm |
| First four minutes | One step calmer |
| Climb that lasts 20 seconds or more | One step up |
| Slower than the pace goal for 45 seconds (not on easy days) | One step up |
| Last 15% of a distance or time goal | Push, at once (one step up on easy days) |
| Goal reached | Calm |
| Standing still or no fresh GPS | No change |

A new level must hold for 20 seconds before it counts. A playlist stays at least three minutes, and a change waits for the gap between two songs. Only the final stretch cuts into a song.

Runline steers only music that plays from one of the three playlists. If you choose anything else in Spotify during the run, that choice stands until you return to one of them. If your music is already playing when the run starts, Runline does not replace it.

Runline starts music only on the active Spotify device or on this account's phone, never on a speaker or TV that happens to be online. If Spotify is not running, Runline sends the play key, which restarts the last player. If Spotify still does not appear, the strip shows **Open Spotify**; opening it within three minutes starts the chosen playlist.

## What it does not do

- **No AI on Spotify data.** Spotify's Developer Terms forbid passing Spotify content, including track and playlist names, into an AI model. The optional AI coach never receives anything from Spotify, and no track is rated or chosen by a model. The run sets the mood; your playlists hold the music.
- **No tempo matching per song.** Spotify closed its audio-features endpoint (tempo, energy) to new apps in November 2024. Mood comes from which playlist you put a song in.
- **No track lists are read or stored.** Runline lists your playlists' names so you can pick three, and keeps only those three names and IDs.

## Data and security

- Requested permissions: read and control playback, and list your playlists (`user-read-playback-state`, `user-modify-playback-state`, `playlist-read-private`, `playlist-read-collaborative`). No email, profile details or listening history.
- The access and refresh tokens are AES-256-GCM ciphertext under a non-exportable Android Keystore key, in `noBackupFilesDir`. They never enter exports, logs, instance state or backups. This lowers extraction risk; it does not protect an unlocked or compromised phone.
- Requests go only to `accounts.spotify.com/api/token`, `api.spotify.com/v1/…` and, for the cover, `i.scdn.co/image/…`. Redirects are not followed. Nothing about the run is sent to Spotify: no location, pace or goal.
- The sign-in answer arrives through the `runline://spotify-callback` link. It carries a one-time code that is useless without the verifier Runline kept, is checked against a random state, and is ignored unless a sign-in started in the last 15 minutes.
- **Disconnect** deletes the tokens, the Keystore key and the chosen playlists. To revoke access on Spotify's side as well, remove the app under **Account → Apps** on spotify.com.

## Limits

- While recording, Runline asks Spotify what is playing about every ten seconds. A change you make in Spotify can take that long to show.
- The voice coach still lowers the music through Android audio focus while it speaks; that is unchanged and independent of this module.
- Spotify can change its API rules again. The February 2026 rules this was built against are listed in [Spotify's migration guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide).
- Spotify is a trademark of Spotify AB. Runline is not affiliated with or endorsed by Spotify.

Verification scope is recorded in [TESTING.md](TESTING.md#version-023-music-and-spotify).
