# Agent contract

AI agents write Runline's code. A change merges only when deterministic evidence shows it is safe, not because an agent says so. Every agent (Claude, Codex or any other) follows this contract. The gates in `tools/trust/` enforce it, and the owner controls the gates.

## Definition of done

1. `python3 tools/trust/verify.py` passes locally. It runs every gate in a fixed order and writes `build/reports/trust/verify.md`.
2. The pull request body contains that report's table. It names the tests that prove the behavior change and lists what was **not** verified: a physical phone, a real AI provider, Spotify, battery life or anything else outside the gates.
3. Every GitHub check passes on the exact PR head commit. A green earlier commit does not count, and neither does a pending, cancelled or skipped required check.

## Commands

```sh
python3 tools/trust/verify.py            # every gate, including Gradle tests, builds, lint and coverage
python3 tools/trust/verify.py --static   # every gate except Gradle (about 30 seconds)
python3 tools/trust/verify.py --stage kotlin-analysis
python3 tools/trust/verify.py --fix      # ktlint and ruff formatting, comment removal
python3 tools/trust/run_detekt.py --update-baseline         # after fixing recorded detekt findings
python3 tools/trust/check_bytecode.py --base-ref origin/main # prove compiled classes did not change
```

The first run downloads ktlint, detekt and ruff into `build/trust-tools/`, each pinned to an upstream SHA-256 in `config/trust/toolchain.json`. A JDK 17, Python 3.12+ and the Android SDK are required.

## Rules the gates enforce

| Rule | Gate |
| --- | --- |
| No comments or docstrings in Kotlin, Java, Gradle, Python, XML, YAML, TOML or properties files. Names carry intent; rationale belongs in the PR, the commit message or `docs/`. Three exceptions: release tags beside pinned action SHAs, the generated icon provenance header and Semgrep fixtures. | `comments` |
| Kotlin is formatted by ktlint (`ktlint_official`, 140 columns). Python is formatted by ruff. | `kotlin-format`, `python-format` |
| detekt runs every rule except five layout and taste rules that conflict with ktlint or idiomatic Kotlin (`config/detekt/detekt.yml`). Ruff runs every rule except those listed in `pyproject.toml`. Android lint runs every check, with warnings as errors. | `kotlin-analysis`, `python-lint`, `gradle` |
| Packages depend only on the layers declared in `config/trust/policy.json`. `core` is pure Kotlin: no Android, no network and no resources. A new package must be declared there first. | `architecture` |
| Debt only shrinks. `config/detekt/baseline.xml`, `app/lint-baseline.xml` and `config/trust/debt.json` may lose entries, never gain them. When you fix a recorded finding, remove its entry in the same change. | `ratchets`, `kotlin-analysis` |
| No new suppressions: `@Suppress`, `@SuppressLint`, `@SuppressWarnings` or `tools:ignore`. Fix the finding instead. | `ratchets` |
| The test count never falls. Never delete, weaken or disable a test to make a build pass. | `test-integrity` |
| JVM tests are deterministic: no `Thread.sleep`, host clock, unseeded randomness, `@Ignore` or default-locale changes. Inject time and randomness. | `test-integrity` |
| Line coverage per package never drops below its floor in `config/trust/coverage.json`. Floors only rise; raise one when the gate reports it is exceeded. | `coverage`, `ratchets` |
| Gate files change only under the `gate-change` label (see below). The lint and coverage settings in `app/build.gradle.kts` stay on. | `gate-integrity` |
| Tools and dependencies are pinned. New tool versions must be at least 14 days old, and Dependabot waits 14 days before proposing an update. | `toolchain` |
| Each gate is itself tested. Seeded bad changes (a comment, a deleted test, a layer violation, a grown baseline, a new suppression, a lowered floor, an unlabeled gate edit) must be rejected. | `trust-evals` |

## Engineering rules

detekt enforces the numbers below. Reviewers enforce the rest.

- Functions have at most 60 lines, cyclomatic and cognitive complexity of 15 or less, at most 6 parameters and nesting depth of 4 or less. Classes have at most 600 lines and 11 functions.
- Put decision logic in `core` as pure functions with JVM tests. Keep Android classes thin: they translate events and render state.
- Inject the clock, randomness, files and network so behavior is testable and repeatable.
- Catch specific exceptions. Never swallow one silently.
- Give numbers names. A literal in a comparison or calculation needs a named constant.
- Make one change per pull request. Do not mix refactors with behavior changes. For a formatting, rename or refactor change that should not alter behavior, add the `no-behavior-change` label: CI then compiles the base and the head and fails if any compiled class differs.
- Do not add a library, Gradle plugin, permission or network endpoint without the owner's written approval in the task.

## Product invariants

- Never rewrite recorded GPS coordinates or heights. Smoothing and terrain affect only display and analysis.
- Recording never depends on maps, terrain, network or AI.
- When terrain or grade is uncertain, show unknown and give no hill coaching.
- AI is opt-in and uses the runner's own key. Keys never reach recordings, exports or logs.
- English and Spanish resources stay in parity.
- Never commit keys, signing material, real activity exports, health notes, photos or private logs. Use synthetic fixtures.

## Labels

- `gate-change`: required when a change touches a protected path in `config/trust/policy.json`, such as workflows, linter configuration, trust tools, `AGENTS.md` or the security checks. **Only the owner adds this label.** An agent never adds it, and never weakens a gate, baseline, floor or threshold to get a change through. If a gate is wrong, stop and explain why in the PR.
- `no-behavior-change`: an agent may add it. CI then proves the claim with `check_bytecode.py`.

More detail: [checks](docs/CHECKS.md), [development](docs/DEVELOPMENT.md), [product](PRODUCT.md), [design](DESIGN.md).
