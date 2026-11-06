# Runline 0.17 plan

Feedback after a 4.7 km run on 6 November 2026 through streets, over a highway bridge and through an underpass, compared with the 10K park-loop run of 4 November. Private GPX files and the coach log were analysed locally and stay outside Git.

## Findings

- **Slope at bridges and underpasses.** The independent terrain model describes the ground, not a bridge deck or the floor of an underpass. Around those structures its 9-point footprint check failed, and each failed fix silently fell back to coarser 30 m global heights. The two models differ by up to ±12–16 m in this area, so the mini profile zig-zagged between them. Nine height jumps of 1.5 m or more came from that mixing. GPS height alone is no better: through the underpass it fell 16 m and rose 21 m, far more than the real ramps.
- **Mini profile scale.** The 45-second slope line used a 3 m minimum vertical range. Two metres of change over 130 m filled the whole line and looked like a cliff.
- **Stops.** Five standstills (traffic lights, shoelaces) totalled 2 min 38 s. They counted as running time, so average pace showed 6:57 /km instead of 6:23 /km moving. The 10K had none.
- **Coach.**
  - The "settling in" line played while standing at a traffic light.
  - Hill-recovery advice played four times after −2% stretches.
  - Four generic "steady" lines carried no numbers.
  - Kilometre cues gave only a whole-run average that included stop time.
  - Halfway and final-stretch lines said nothing measured.
- **Export.** "Share file" did not say it shares the GPX. Sharing everything one run recorded needed several separate exports.
- **Runner profile.** Experience was a manual choice that goes stale as training changes.

## Changes

1. **Terrain structures.** Where the detailed regional model covers a place but its footprint is ambiguous, the height is unknown instead of falling back to global data. Heights within 15 m on either side of such a fix are unknown too. A live, forward-only variant protects coaching. Recorded GPS and exports are unchanged.
2. **True-aspect slope line.** Its vertical range is at least a 12% grade over the drawn distance. A brief unknown between two estimates holds the last label for up to 5 s instead of flipping.
3. **Stop detection and auto-pause.** Standstills are derived from the saved fixes, so older activities benefit too. A live signal uses every valid fix, including those too close to store, and Doppler speed. With **Auto-pause at stops** (default on), the timer, average pace, splits, notification, kilometre cues and time goals use moving time. Fixes are never removed. Details show moving time, elapsed time and stops; CSV and AI summaries gain moving and stop fields.
4. **Coach.**
   - Silent while stopped; pending cues are dropped. After moving again, pace and morale wait 20 s.
   - Kilometre cues give that kilometre's moving split and the change from the previous one.
   - Halfway, final stretch and goal reached give measured time, distance and projection.
   - Generic steady lines become short progress checks with a trend: picking up, steady, or eased.
   - Hill recovery only follows a real climb: 60 m reaching 3%, or 120 m uphill.
5. **Export.** Download GPX, Share GPX and Save GPX to… now have icons. **Share all data (ZIP)** and **Download all data (ZIP)** bundle:
   - the GPX
   - every fix with derived terrain and stop columns
   - stops
   - the coach log
   - a measured summary
   - any saved review

   Photos and the runner profile are never included.
6. **AI-managed runner profile.** A local training snapshot shows recent volume, longest run, typical moving pace and average effort. With **Let AI keep my profile up to date**, the AI assesses experience and writes a two-sentence summary and one focus. This runs after new runs (at most every 6 h) and on demand. The assessment lives in a separate store. Health notes still need the separate sharing consent.

## Acceptance

- Unit tests:
  - stops, moving time, splits and the live tracker
  - structure guard, live guard and slope scale
  - stop-aware, measured coach cues
  - ZIP contents
  - training snapshot, insights parsing, update cadence and consent
- Local replay of the private runs:
  - no global-model mixing
  - zero false stops on the 10K
  - one hill-recovery cue today, after the real climb out of the underpass
- Signed update retains existing recordings on the emulator. All six required checks pass on the exact PR head before merging.
