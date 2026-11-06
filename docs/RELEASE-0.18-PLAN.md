# Runline 0.18 plan

Feedback after watching the 6 November street run in 0.17. Around the Granada ring-road crossings, on a road over another road and under the highway bridge, the terrain data cut out, so the slope line and elevation graph broke there. The replay also needed a clearer recenter control, smaller credits and a trail that grows behind the arrow. The private GPX stays outside Git.

## Findings

- **Why it cut.** 0.17 hid every ambiguous structure footprint plus 15 m either side. On the 6 November run that was 63 fixes in 4 stretches of 42–75 m, and every chart and the slope line broke at each one.
- **What the model shows there.** The regional model keeps the ground and removes bridge decks:
  - On the road over another road (about 1.7 km), its heights **dip** 3 m at a 26% grade where the runner stayed level.
  - Under the highway (about 2.2 km), the embankment makes its heights **hump** 5.6 m at a 44% grade over a flat passage.
  - Two other stretches were smooth ramps beside walls (10–23%), hidden only because the wider footprint touched the walls.
- **GPS height does not help.** Near the structures it jumped by up to 5 m in a few metres.

## Changes

1. **Structure levels.** Each hidden stretch gets a level, using clean regional heights on both sides.
   - Where the model's own heights through the stretch stay runnable (at most 15% over any 10 m), they are the surface: **ground**.
   - Otherwise the runner was on a level the model does not hold: **above** it (bridge, overpass) when the model dips, or **below** it (underpass) when the model humps. The height is the straight join between the clean sides.
   - Stretches over 250 m, joins steeper than 30%, or stretches without clean heights on both sides stay unknown.
   - Live coaching keeps its forward-only guard.
   - Recorded GPS is never changed; `points.csv` gains a `terrain_level` column.
2. **Recenter arrow.** The follow control uses the same arrow as the map marker. It sits at the bottom right, level with the credits, and appears only after the map is panned away from the arrow.
3. **Discreet credits.** The attribution and its information icon become one small translucent pill. Its whole 48 dp row still opens the credits.
4. **Trail behind the arrow.** Replay draws only the path already covered, ending exactly at the arrow. A seek redraws it. At the end, the whole route is shown.
5. Version 0.18.0, code 21.

## Not covered

- Field use on a physical phone.
- Long viaducts over flat valleys: the flat valley floor is not ambiguous, so its heights are shown as the surface.
