# Family TV Remote progress

- Build day: **5 / 14 — code approved and published; physical acceptance blocked**, issue #5.
- Current milestone: Day 5 foreground-only WSS remote pad published after owner approval; local tests/builds pass. Days 2–5 physical gates remain open.
- Baseline: Java launcher `/launcher`, legacy Android `/remote`, TCP/HMAC `/shared`, existing build/regression GitHub Actions. The protocol folder exists; Flutter `/mobile` is now scaffolded with shared Android/iOS pairing source.
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

## Day 3 — approved publication (2026-11-17)

Issue #3: https://github.com/DarkCodexUser999/family-tv/issues/3. Owner approved publication after reviewing the local test results. See DAY_3_REVIEW.md.

- TV-opened 120-second one-use six-digit PIN, five guesses/window, address/global throttling surviving window reopen, maximum eight phones. Raw tokens are returned once over TLS; only SHA-256 hashes persist privately. No phone names or secrets are advertised.
- Strict <=1024-byte pairing JSON, duplicate keys rejected, shared monotonic request deadline, bearer gate for all non-public requests. No WebSocket session or control endpoint is implemented yet; upgrades are rejected after authentication.
- Event-based NSD registration/cleanup, manual address fallback, explicit revoke-all confirmation, PIN dialog one-shot dismissal. No polling, new process, runtime dependency or accessibility integration.
- Local checks passed: 47 TLS/transport checks, 30 pairing/auth/persistence checks, 13 discovery lifecycle checks with host Android fakes, all existing regressions, 27 protocol fixtures, both signed APK builds. Flutter checks N/A: /mobile not created. Each exact staged snapshot passed checks and both builds before commit/push. Final GitHub CI evidence is recorded on the project card after the last push.
- Fire OS keystore, NSD on home Wi-Fi/two phones, TV UI, shutdown/disabled state, same-build PSS and input/launch timings remain unverified. No device installation occurred; neither day issue may be closed on host evidence alone.
- Published code: f13f5b6 (pairing core), 0295e50 (bounded TLS/auth), 156fbb2 (discovery/TV settings). This documentation update is the fourth approved increment. Issue #3 remains open for physical acceptance.
- Next: verify final CI and board evidence; Day 4 Flutter skeleton needs its own briefing/go.

## Day 4 — approved publication (2026-11-18)

Issue #4: https://github.com/DarkCodexUser999/family-tv/issues/4. Owner approved the reviewed implementation and four-commit publication. See DAY_4_REVIEW.md and MOBILE_DEPENDENCIES.md.

- Shared Flutter Android/iOS source, discovery/manual endpoint, exact pinned TLS, PIN entry, secure saved pairing, local forget and recovery UI. No pad or app controls; launcher unchanged.
- Analyzer clean and 14 mobile tests passed, including real HTTPS pin/rejection checks and a rendered phone-size/200%-text layout test. iOS configuration syntax checked; no Xcode/native iOS build.
- Existing Java/protocol checks and both legacy APK builds passed. New Flutter debug APK built and v2 signature verified (API 26+, three Android ABIs). No phone/TV/emulator install.
- Dependencies/outputs/caches stay on SSD. ExFAT metadata and NDK links handled with an SSD-backed APFS tool/build workspace; original filesystem not reformatted. Gradle/NDK downloads verified against upstream checksums.
- Published code: 9fc348c (1), ff302fd (2), f522a20 (3). This evidence/test update is the fourth increment. Each exact staged snapshot passed applicable Flutter/Java/protocol tests, analyzer and all three APK builds before commit/push; final CI links are recorded on the project card.
- Next: retain device acceptance gates and keep issue #4 open. Then Day 5 needs a new briefing/go; physical pairing and storage/discovery gates remain open.

## Day 5 — approved publication (2026-11-19)

Issue #5: https://github.com/DarkCodexUser999/family-tv/issues/5. Owner explicitly approved direct publication and daily end-of-work pushes. Baseline ed46e38. See DAY_5_REVIEW.md.

- Cached launcher lifecycle/focus, bounded two-phone authenticated WSS, expiry/epoch/dedup/rate limits, revoke/stop cleanup. No polling, global key injection, new process or runtime JAR. NanoWSD source/patches reviewed with BSD notice retained and bundled.
- Flutter D-pad/OK/Home/Back/Options, one-action feedback, foreground/busy gating and finite reconnect without replay. App launch routes/config, hidden list, loading behavior, physical BACK and restore script preserved.
- Local analyzer clean; 21 mobile tests plus one actual Dart-to-Java pinned WSS test pass. Java checks: 47 transport, 30 pairing, 13 fake discovery, 29 bridge/bounds and 45 TLS/WSS; protocol 9 positive/20 negative and actual emitted state/ACK pass. Three APK builds/signatures pass.
- Host-rendered 390x844 phone preview/200%-text check; no phone/emulator/TV install or iOS native build. No Fire OS PSS/latency claim. Integrated source passed the full local checks and three APK builds before publication. The first two staged snapshots also passed repeat checks; final integration and evidence use the completed review results without another rebuild. Final CI evidence is recorded on the live project card after completion; code is published and physical acceptance stays Blocked.
- Three cohesive publication groups approved; 3–5 meaningful daily limit retained. Issue #5 stays open for physical acceptance. Session ends by stopping task-started tools, detaching the tools image and safely ejecting ExSSD. Day 6 needs a new briefing/go.

Published code increments:

- [0a8e1f0](https://github.com/DarkCodexUser999/family-tv/commit/0a8e1f026c2b6016595e95cd572128e6577db995): feat: add bounded authenticated WSS event and action transport
- [d94b8d1](https://github.com/DarkCodexUser999/family-tv/commit/d94b8d1cd23748fe76eda234f43befa0d8ab364f): feat: add pinned Flutter remote pad with bounded reconnect

This evidence/progress update is the third approved increment. Issue #5 remains open for physical acceptance; Day 6 is ready for its own briefing/go. No background schedule is implied.

## Live project board

Board: https://github.com/users/DarkCodexUser999/projects/1 (family-tv; public). History, Day 1 and Days 2–14 are tracked with evidence and separate blocked device gates. See PROJECT_TRACKER.md. During approved sessions, link the day issue, move its card to In progress, append tested commit/CI links, then mark accepted scope Done and make the next day Ready. Include board maintenance in the ordinary daily progress commit; no background update schedule or extra contribution-only commits.

## Daily execution agreement

- 3–5 meaningful commits maximum; four planned. No filler, empty commits or artificial splitting.
- Verify email and auth, read current plan/progress, present the day briefing and wait for go.
- After each increment run applicable tests/builds (plus Flutter analyze/test once mobile exists). The latest owner instruction authorizes publishing each approved workday without asking again at the end: finish checks, commit, pull --rebase and push, update the project board, then stop task-started background tools and safely eject SSD. Daily briefing/go still applies before new implementation. Resolve failures in visible follow-up work; never force-push published history.
- Last daily progress commit closes the issue only when its acceptance passes. Give a five-line report/demo and tomorrow preview.
- At session end stop only task-started processes, leave SSD folders, detach the SSD-backed APFS image and safely eject ExSSD; never force ejection.
- Alpha tag after Week 1; stable v1.0 only after essential physical acceptance.

## Open gates

- Fire TV remains in another city. API 22 TLS/NSD, pairing, enabled/disabled PSS and focus/launch latency are pending.
- No physical iPhone/signing setup; simulator success cannot establish LAN parity. No emulator configured.
- NanoHTTPD core was reviewed on Day 2; bounded NanoWSD source and patches reviewed in the Day 5 candidate. No independent security audit or Fire OS performance acceptance is claimed.
- Flutter and tool caches stay on SSD where possible; macOS preferences/temporary files can still use internal storage. No secrets or signing material are in the repo.
