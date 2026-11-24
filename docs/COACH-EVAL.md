# Live coach eval and runner council

This eval measures one flow: what the live voice coach says during a run. It replays whole runs through the real coaching code (`CoachEngine`, `LiveCoachPlan`, `LivePoints`, `LiveAiAdvice`, the speech schedule) with real calls to the production live model, then has a council of runners score how much the coach helped.

## Cases

25 runs. 20 are synthetic scenarios committed in [`app/src/test/resources/coach-eval/scenarios.json`](../app/src/test/resources/coach-eval/scenarios.json) and generated deterministically by `SyntheticRuns` (seeded GPS noise, late fixes, hills, stops, pauses, tunnels). 5 are the owner's recorded runs, kept outside the repository with their real coach logs.

| id | family | language | goal | what happens |
| --- | --- | --- | --- | --- |
| s01-walk-run-first-5k | beginner | English | 30 min, easy | 3 min running, 1 min walking, seven times |
| s02-steady-8k | steady | English | 8 km, steady | even 5:12/km on a flat out-and-back |
| s03-hilly-loop-7k | hills | Spanish | 7 km, steady | two laps with a 600 m climb at 6% |
| s04-long-run-fade-18k | long | Spanish | 18 km, easy | last 3 km slow from 6:20 to 7:00/km |
| s05-recovery-too-fast | recovery | Spanish | 4 km, recovery | starts far too fast after a hard day |
| s06-10k-race-fade | race | English | 10 km in 50 min | 4:50/km out, fades to 5:25/km, misses the target |
| s07-city-traffic-lights | stops | Spanish | none | four traffic-light stops |
| s08-fartlek-40min | intervals | English | 40 min, steady | eight rounds of 1 min fast, 2 min easy |
| s09-comeback-after-break | comeback | Spanish | 20 min, easy | first run in over three weeks |
| s10-gps-tunnel | gps | Spanish | 5 km, easy | noisy GPS, two minutes without signal |
| s11-half-marathon-sim | long | English | 21.1 km, steady | first half marathon distance, faster finish |
| s12-slow-beginner-3k | beginner | Spanish | 3 km, easy | 8:30/km with a walk break, at 2240 m |
| s13-downhill-out-uphill-back | hills | English | 6 km, easy | 100 m down on the way out, up on the way back |
| s14-track-5k | track | Spanish | 5 km, steady | 12.5 laps of a 400 m track |
| s15-beyond-goal | beyond | English | 5 km, easy | keeps going to 8 km |
| s16-progression-8k | progression | Spanish | 8 km, steady | each kilometre 10 s faster |
| s17-long-pause | stops | English | 45 min, easy | recording paused for five minutes |
| s18-heat-struggle | struggle | Spanish | 8 km, steady | slows sharply, walks, finishes |
| s19-tempo-pace-band | tempo | English | pace band 4:50-5:10 | drifts out of the band for a kilometre |
| s20-sparse-gps | gps | English | 35 min | a fix only every 3 s, weak accuracy |
| five recorded runs | real | Spanish | various | the owner's laps in a park, 3 to 10 km |

Split, drawn at random within each family: train (read while improving) 2 recorded runs and 10 scenarios; test (only scored) 3 recorded runs and 10 scenarios. The headline is the test score.

## Grading

**Runner council** (`LiveCoachCouncil`): five runner profiles each rate every run as if they had just run it with this coach in their ear: a beginner, a pace-driven racer, a half-marathon trainee, a certified coach judging the session's intent, and a music lover who wants few interruptions. Each gives helped, trust, motivation, variety and timing (0-10), would keep the coach on (yes/no), overall (0-100, anchored: 20 a talking stopwatch, 50 pleasant but generic or with errors, 70 genuinely helpful, 85 excellent), the best and worst line and the one fix that matters most. The headline metric is the council's mean overall score.

**Line judge** (`LiveCoachBacktest.judge`, a guardrail): grades every line for accuracy, clarity, specificity, value, voice, freshness and whether it asks for a reply. Wrong lines and lines asking for a reply must not rise.

Both judges use structured output and treat the transcript as data. Results land per run in `results.jsonl` with token usage and cost for the coach and the judges, so cost is measured, not estimated.

## Running it

```sh
set -a; . ~/.config/runline-dev/anthropic.env; set +a
export RUNLINE_BACKTEST_DIR=<private eval directory> RUNLINE_BACKTEST_VARIANT=hillclimb/v1
RUNLINE_BACKTEST_STEP=replay ./gradlew testDebugUnitTest --tests '*LiveCoachBacktest.replay*' --rerun-tasks
RUNLINE_BACKTEST_STEP=judge RUNLINE_BACKTEST_JUDGE_REPS=1 ./gradlew testDebugUnitTest --tests '*LiveCoachBacktest.judge*' --rerun-tasks
RUNLINE_BACKTEST_STEP=council ./gradlew testDebugUnitTest --tests '*LiveCoachCouncil.council*' --rerun-tasks
```

Without `sessions.json` in the directory only the synthetic scenarios run, so anyone can run the eval without private data. `RUNLINE_BACKTEST_MODE=local` replays with the built-in coach only and makes no calls. A failed council call goes to `errors.jsonl` and is retried by running the step again; finished grades are kept.

## Results for 0.25

Seven rounds on the live coach (`claude-haiku-5-5`), each scored on all 25 runs: council and line judge on Sonnet 5.5, one replay and one judge pass per run. A fresh analyzer read only the train runs each round; the test runs were only scored.

| round | change | test council | train council | keep on | trust | wrong lines/run | lines/10 min | cost/round |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 0.24.4 | 40.6 | 36.9 | 26% | 3.5 | 1.76 | 6.2 | $7.76 |
| 1 | points agree with the run data at the moment they are spoken | 41.8 | 39.4 | 40% | 3.8 | 1.68 | 6.2 | $7.67 |
| 2 | pace judged against the session plan (paced train runs only: 31.8 to 39.5) | | | | | | | $0.84 |
| 3 | news first; filler waits for room and plays once | 41.9 | 46.2 | 55% | 4.4 | 1.44 | 4.3 | $5.63 |
| 4 | kilometre/lap numbers and body claims checked | 42.8 | 45.4 | 52% | 4.4 | 1.48 | 4.3 | $5.78 |
| 5 | walk, stop, pause, GPS-gap and fast-block events | 42.8 | 43.3 | 50% | 4.1 | 1.42 | 4.4 | $5.74 |
| 6 | fast blocks dropped (false positives on real GPS) | 42.8 | 46.0 | 60% | 4.5 | 1.40 | 4.3 | $5.69 |
| 7 | milestones look ahead; halfway stops estimating | **43.5** | 45.2 | **62%** | 4.3 | 1.60 | 4.3 | $5.61 |

Paired against the baseline over all 25 runs, round 7 scores +5.5 ±2.6 council points, +36 ±11 points of keep-on, +0.8 ±0.5 trust and +0.6 ±0.2 variety; wrong lines are −0.2 ±0.9, within noise. On the 13 test runs alone the gain is +2.8 ±3.5, inside the noise of a 13-run set. The split put every paced run in train, so the plan-relative pacing gain shows only there. Calibration: a silent coach scored 11 to 22, the built-in coach 36 and a coach with wrong figures 12 to 15, all below the AI coach. Total spend about $46.

Not measured: a real phone, real GPS beyond the five recorded runs, Spanish text-to-speech over music, and latency on mobile data. Several runs move ±5 to 10 points between identical replays.
