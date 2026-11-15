# Family TV Remote progress

- Build day: **1 / 14 — in progress**, issue #1.
- Current milestone: Step 1 architecture, 14-day plan and progress/ignore setup.
- Baseline: Java launcher `/launcher`, legacy Android `/remote`, TCP/HMAC `/shared`, existing build/regression GitHub Actions. New Flutter `/mobile` and `/protocol` are not created yet.
- Contribution identity verified: `289677099+DarkCodexUser999@users.noreply.github.com` matches GitHub account `DarkCodexUser999` (ID 289677099).

## Done

- Inspected repository, contribution email, current architecture, build/test gate and clean working tree.
- Defined the opt-in, bounded LAN server, TLS pairing/token model, semantic control limits, v1 contract, resource targets and migration gates in ARCHITECTURE.md.
- Planned fourteen implementation days in PLAN.md, each with four proposed working commits, acceptance and a phone/TV demo.
- Extended secret/build exclusions for the future Flutter and protocol workspace.
- Step 1 validation uses existing Java checks and both APK builds; no new runtime behavior is introduced.

## Next — await the daily planning request, then “go”

Day 1: protocol fixtures and toolchain baseline. First present goal, scope/why, launcher connection, files, ordered commits, estimate and risks; end with **Say go.** Only build after explicit go.

Before the first implementation commit, provision `gh` and Flutter on ExSSD, verify auth/email and pin the Flutter SDK. Open one day-goal issue using `gh issue create`. The tools are being provisioned on ExSSD; do not claim Flutter analyzer success until installed and a Flutter package exists. No tool installation is part of Step 1.

## Daily execution agreement

- Read this file and PLAN.md on each “today” request. Day counter advances after acceptance/report, not merely on a calendar change.
- Work on main; meaningful, compilable increments only. Four commits/day is a planning target; the ceiling is 3–5 meaningful commits, not permission for filler or failing commits. Adjust the plan transparently if dependencies/device evidence require it.
- After each logical increment run applicable unit tests and `flutter analyze`/`flutter test` once mobile exists, commit, `git pull --rebase`, then push immediately. On conflict stop and resolve without force-push; do not rewrite published history.
- Report one line per pushed commit: hash + conventional message. The final progress commit closes the day's issue using `Closes #N` in its body.
- Commit/push PROGRESS.md at the end of every day; report five lines, demo and tomorrow's preview.
- Week 1: candidate annotated tag `v0.1.0-alpha.1` only after gates. Week 2: `v1.0.0` only with essential device acceptance; otherwise prerelease and document the reason.

## Validation and blockers

- Step 1: Java unit/configuration checks and existing signed APK builds are the applicable local gates. Flutter analyzer/tests: **not applicable yet**, `/mobile` absent and SDK not installed. Step 1 is documentation/configuration only.
- Physical Fire TV is in another city; no device session is available. Fire OS TLS/NSD, pairing, enabled/disabled PSS and focus/launch latency gates remain **pending**.
- Physical iPhone/signing setup unavailable; simulator success will not imply physical-device parity or TestFlight availability.
- No emulator is configured. Do not claim emulator or TV results from host tests.
- Resource values in ARCHITECTURE.md are design ceilings, not measured results. Real server choice is gated on bounded connections, API 22 and TLS feasibility.
- GitHub CI must stay green; link actual runs/results in subsequent daily entries, without fabricating outcomes.
