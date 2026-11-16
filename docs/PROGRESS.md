# Family TV Remote progress

- Build day: **2 / 14 — code published; physical acceptance blocked**, issue #2.
- Current milestone: Day 2 implementation approved and published; device acceptance pending.
- Baseline: Java launcher `/launcher`, legacy Android `/remote`, TCP/HMAC `/shared`, existing build/regression GitHub Actions. The protocol folder now exists; the new Flutter `/mobile` app is not scaffolded yet.
- Contribution identity verified: `289677099+DarkCodexUser999@users.noreply.github.com` matches GitHub account `DarkCodexUser999` (ID 289677099).

## Day 1 — complete (2026-11-15)

Issue: #1, closed by the final progress commit. Four increments only: toolchain, contracts, fixtures/CI and security/progress. All work is in the existing repo on main.

- Installed gh 2.104.0 and verified GitHub identity matches the configured noreply email.
- Flutter 3.50.0 / Dart 3.14.0 bootstrapped successfully; revision ab2912035e160d9cad6306df307576da6128ebdb pinned. Analytics disabled.
- ExFAT hard-link lock failure resolved using an 8 GiB-capacity sparse APFS image backed by ExSSD, mounted at /Volumes/FamilyTVTools. Drive was not reformatted. Mount before Flutter commands; detach before ejecting SSD.
- Validator environment and pinned dependencies live on ExSSD. `protocol/openapi.yaml` and `events.schema.json` define foundational pairing/status/apps/actions and revisioned configuration contracts only.
- Protocol fixtures: 8 positive and 19 negative cases passed; OpenAPI/schema checks, auth/revision guards and existing Java regression tests passed. Both existing signed APK builds passed.
- CI for contract tests and both APK builds passed: https://github.com/DarkCodexUser999/family-tv/actions/runs/43674959104. The final documentation commit triggers another run; inspect its actual status.
- Flutter analyze/test: N/A because no Flutter package exists yet. No runtime server, app UI, emulator or device result is claimed.

## Day 2 — approved publication (2026-11-16)

Issue #2: https://github.com/DarkCodexUser999/family-tv/issues/2. See DAY_2_REVIEW.md and protocol/THIRD_PARTY.md. The owner approved the reviewed four-commit publication. The default-off bounded HTTPS transport, settings toggle, lifecycle and host tests are now in source. No TV installation occurred. Legacy TCP autostart is disabled; the old native companion is temporarily incompatible with this candidate. New pairing and commands are not implemented yet.

Applicable local gate: existing regressions, 36 real TLS/bounds/shutdown checks, 27 protocol fixtures, live Info JSON schema validation and both signed APK builds. Flutter analyze/tests remain N/A with no /mobile package. Physical TLS, service lifecycle, PSS and focus/launch latency are blocked on access to the stick. GitHub CI now includes the transport checks; final run evidence is linked from the project card after completion. No passing device evidence is implied.

Published code increments: 25eb2d1 (dependency/build), d9fb455 (transport/host tests), dd3bbbb (service/settings). This progress update is the fourth approved commit. No empty or contribution-only commits.

Next: retain Day 2 issue/device gates until acceptance; propose Day 3 only in the next briefing and wait for explicit go. No new pairing or phone app is claimed.

## Live project board

Board: https://github.com/users/DarkCodexUser999/projects/1 (family-tv; public). History, Day 1 and Days 2–14 are tracked with evidence and separate blocked device gates. See PROJECT_TRACKER.md. During approved sessions, link the day issue, move its card to In progress, append tested commit/CI links, then mark accepted scope Done and make the next day Ready. Include board maintenance in the ordinary daily progress commit; no background update schedule or extra contribution-only commits.

## Daily execution agreement

- 3–5 meaningful commits maximum; four planned. No filler, empty commits or artificial splitting.
- Verify email and auth, read current plan/progress, present the day briefing and wait for go.
- After each increment run applicable tests/builds (plus Flutter analyze/test once mobile exists). The latest owner instruction requires approval before committing/pushing; after approval, pull --rebase and push each approved increment to main. Resolve failures in visible follow-up work; never force-push published history.
- Last daily progress commit closes the issue only when its acceptance passes. Give a five-line report/demo and tomorrow preview.
- Alpha tag after Week 1; stable v1.0 only after essential physical acceptance.

## Open gates

- Fire TV remains in another city. API 22 TLS/NSD, pairing, enabled/disabled PSS and focus/launch latency are pending.
- No physical iPhone/signing setup; simulator success cannot establish LAN parity. No emulator configured.
- NanoHTTPD/NanoWSD version, security/resource caps and license provenance still require implementation-time review.
- Flutter and tool caches stay on SSD where possible; macOS preferences/temporary files can still use internal storage. No secrets or signing material are in the repo.
