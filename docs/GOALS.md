# Goals and today's suggestion

Set an optional overall aspiration under **Settings → AI → Overall goal**, for example “Half marathon in April.” This is context for the coach, not a calendar planner. The legacy plan contributes its concise goal once; an explicitly cleared goal stays empty.

Enable **Daily suggestion** in AI to prepare a next-run recommendation automatically on the first idle Run screen visit each local day. Record shows distance or duration, an evidence-supported optional pace band, and Easy/Recovery/Steady. Rest stays simply Rest. **Use** selects that target for the upcoming session, independently of speech and Start. Manual distance, pace, time and combined targets remain under **Record → … → Goal** without AI.

Claude receives comparable recorded history, optional effort, calendar-week and trailing-seven-day volume, actual hours since the last completed run and your overall goal. Notes/profile require their separate consent. Sparse or missing history remains unknown; a gap alone does not establish recovery. The app does not impose a longest-run ceiling, fixed progression percentage or weekly mileage allowance. It validates supported input ranges and coherent target combinations without clamping valid advice. No readiness or training-safety guarantee is made.

A durable reservation and result prevent automatic duplicate paid calls across reopening, rotation and ordinary clock/timezone changes. There is one automatic provider attempt per local day; failure, interruption or material context changes require deliberate retry, with a one-minute cooldown. Recording is always available without a suggestion. AI/provider failures do not apply a target.

At Start, the selected target is saved separately from original GPS data. Voice and AI coaching use this session snapshot through pause/recovery, and a review can compare intent with the finished activity even after global settings change. Exceeding a goal neither stops capture nor changes the original target. A new session starts with Use off; older recordings without a snapshot have unknown intent.

The provider returns strict nine-field structured JSON. JVM tests cover cache/retry/stale-result behavior, rest and pace-free targets, history-led progression and malformed outputs. Native tests and their scope are recorded in [TESTING.md](TESTING.md). API usage remains billable to the user's Anthropic account; coordinates/photos never enter these prompts.
