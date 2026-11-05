# Terrain estimates

Runline keeps original GPS heights. The island and coach use a separate, causal estimate of recent grade; neither rewrites the recording or claims a terrain survey.

## Why uphill can look downhill

Horizontal location accuracy is not altitude accuracy. Android reports these separately. A gradual vertical GPS drift can look like a consistent downhill even on a real climb. A short, straight regression can have a small residual and still be wrong. Satellite imagery is a photograph, not elevation data, and newer imagery does not fix a recorded altitude. See [Android Location](https://developer.android.com/reference/android/location/Location#getVerticalAccuracyMeters()).

Version 0.13:

- Retains reported vertical accuracy for new fixes, recovery, local coach logs and GPX extensions. Older files remain readable with unknown vertical accuracy; no precision is invented.
- Rejects reported vertical uncertainty above 8 m for the grade/profile, while preserving the original fix. Horizontal accuracy must still be within 20 m for grade.
- Uses 5 m distance bins, median heights and a robust slope fit across 55–90 m of continuous movement, with at least eight fixes and 20 seconds. Work and memory are bounded.
- Treats abrupt height relocations as a new observation window, not a hill. The height profile breaks across those jumps, poor heights and gaps without deleting observed time.
- Requires agreement with the recent 35 m window. Reversals, leveling and substantial disagreement invalidate the old direction immediately; the app does not smooth an old downhill through a newly observed climb.
- Smooths accepted percentages over traveled distance and keeps the existing direction hysteresis. Supported steep terrain remains possible; there is no park-specific grade cap or automatic sign reversal.

The shared labels are **Uphill / Downhill / Flat** and **Subida / Bajada / Llano**. Unknown stays **Terrain — / Terreno —**. A brief flip marks label changes; pause, seek and Android Remove animations settle immediately. The mini-profile eases toward observations without adding artificial motion.

These checks reduce jumps and noisy direction changes. They cannot prove the true direction when altitude drifts smoothly. Accumulated climb/descent still uses its separate 3 m deadband; it is not retroactively terrain-corrected.

## Inspect an activity

In replay → Details → Elevation, tap or drag horizontally across the chart. The cursor, observed time, distance, relative height and terrain reading follow the selection, and the replay resumes from there. A vertical gesture scrolls the details. The native slider provides keyboard and screen-reader access. Unknown heights remain gaps; the cursor never invents a height there. Edit and AI review are in the fixed header's overflow menu; review is available when configured or already saved.

## Independent terrain data

A barometer, calibrated across the session, or a suitable bare-earth elevation model could add independent evidence. Spain's IGN publishes [PNOA LiDAR terrain models](https://pnoa.ign.es/pnoa-lidar/productos-derivados), including 5 m grids. Model resolution, date, horizontal alignment, paths, bridges and terrain changes still matter. Such a comparison should download a suitable area and process the route locally, retain provenance, and keep measured versus corrected heights distinguishable.

No terrain service or additional route upload was added in 0.13. The reported 10K descent has not been independently confirmed: its original GPX was unavailable at the previous local path during this change, and the earlier private test retained aggregate summaries only. Regression tests use synthetic altitude jumps, noise, poor vertical accuracy, gradual slopes and genuine steep descents. Recheck the exact section when the original activity becomes available.
