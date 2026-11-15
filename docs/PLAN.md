# Family TV Remote — 14 implementation days

Step 1 is planning only. Day 1 starts only after the daily briefing and explicit **go**. Days are sessions, not guaranteed calendar dates; Days 6–7 and 13–14 are the lighter weekend-style sessions. Time estimates are focused work, excluding tool downloads and device access. Reserve an extra session if physical validation blocks a milestone.

## Execution and quality rules

- On “today”, read PROGRESS.md and this plan; explain Day N goal/why, launcher connection, files, numbered commit list, estimate and risks, then end **Say go.** Do no implementation before go.
- Before each day's code, verify email, provision/authenticate `gh` as needed and open exactly one goal issue via `gh issue create`. Record its number in PROGRESS.md. Day 1 setup precedes issue creation if gh is missing.
- After every logical increment run applicable Java/unit checks and, once `/mobile` exists, `flutter analyze` and `flutter test`; build the affected application before committing. Before then Flutter checks are N/A, not passed. Run appropriate transport/fixture tests too.
- Make a conventional commit, then `git pull --rebase` and push immediately on main. Use `--autostash` only to protect unrelated work; resolve conflicts without force-push or changing published history. One line per pushed commit: hash + message. No empty, filler, whitespace-only or deliberately failing commits.
- Each day's four entries below are proposed cohesive increments, with a 3–5 meaningful commit daily ceiling, not a contribution quota. Aim below ~100 handwritten lines/commit; document necessary generated-scaffold, schema or detailed-document exceptions. If work does not support eight meaningful increments, explain the smaller count rather than manufacture history.
- Final daily progress commit includes **Closes #N** in the body only if acceptance is met. If blocked, keep the issue open and record the blocker instead. Update PROGRESS.md and push; end with a five-line report, demo steps and tomorrow preview.
- End Week 1 with an annotated prerelease tag; end Week 2 with v1.0.0 only if physical acceptance passes. Tags also push after `git pull --rebase`. An explicit pending-validation prerelease is preferable to a false stable release.
- Never claim simulated/host tests prove Fire OS memory, latency, NSD, TLS or other-app control. Current lack of TV/iPhone access is an open gate. No accessibility/root/ADB on phones; no system-wide input injection.

Paths below are planned files, not claims they exist. `launcher/.../lan/` expands to `launcher/src/main/java/app/firetvremote/launcher/lan/`; `launcher/.../LauncherActivity.java` is the current Activity in that same package parent. All tools, SDKs, source, signing files and outputs should live on ExSSD where possible; explain unavoidable macOS/Xcode caches before large setup. Preserve current legacy folders and restore behavior during migration.

## Day 1 — Contract and toolchain baseline
Goal/features and why: Define what the phone may ask the TV to do before transport code exists. Establish reproducible tools and fixtures.
Files: `protocol/openapi.yaml, protocol/events.schema.json, protocol/fixtures/*, protocol/SECURITY.md, docs/SETUP.md, docs/PROGRESS.md`.
Launcher connection: Existing AppCatalog and LauncherBridge boundaries become the source of truth; no changes to existing task flags.
Estimate/risks: 3–4 h; Flutter/gh downloads and GitHub auth may require setup. No Flutter analysis until a mobile package exists.
Commit sequence (four meaningful commits maximum):
1. `chore: establish the remote toolchain baseline`
2. `feat: define the remote protocol contracts`
3. `test: validate remote protocol examples`
4. `docs: record protocol security and day one progress`
Acceptance: Run existing checks and both APK builds; validate schemas and fixtures; verify email and gh auth. Fixture tools must reject invalid examples. No transport exposed.
Demo: Phone: fixture examples only, no installed Flutter UI yet. TV: existing Family TV unchanged.

## Day 2 — Tiny opt-in launcher server
Goal/features and why: Introduce the bounded transport and an explicit TV toggle, keeping new work out of focus/launch paths.
Files: `launcher/.../lan/{LanService,Transport,ServerLimits}.java, launcher/.../LauncherActivity.java, launcher/src/main/AndroidManifest.xml, build-launcher.sh, launcher/build.gradle, protocol/THIRD_PARTY.md, docs/PROGRESS.md`.
Launcher connection: The toggle owns same-process service startup/shutdown. Audit the candidate dependency/license and direct-build integration before accepting it; disable legacy listener autostart to prevent a bypass.
Estimate/risks: 4–5 h; API 22 TLS/keystore and library parser behavior are hard gates. No device result may be inferred from compilation.
Commit sequence (each must pass its applicable gate before push):
1. `chore: pin and document the tiny server dependency and add default-off remote access preference`
2. `feat: bind bounded HTTPS transport to the private Wi-Fi interface and initialize a pinned per-install TLS identity`
3. `feat: serve a minimal versioned info endpoint and close listeners and legacy autostart when remote access is off`
4. `test: cover server bounds and disable lifecycle and record day 2 server evidence and pending device gates`
Acceptance: Compile API 22; test connection limits, public-interface rejection and disabled state with fake bridge. On stick verify TLS handshake, socket closure and disabled/idle PSS before calling the server acceptable.
Demo: Phone: developer HTTPS client can inspect info after fingerprint confirmation. TV: Remote access starts OFF and can be switched ON/OFF.

## Day 3 — Pairing and discovery
Goal/features and why: Make the TV discoverable and pair one authorized phone without exposing credentials on the LAN.
Files: `launcher/.../lan/{PairingStore,PairingWindow,AuthFilter,Discovery}.java, launcher settings UI, protocol/fixtures/pairing*, docs/PROGRESS.md`.
Launcher connection: Explicit TV pairing action exposes a timed PIN and certificate fingerprint; NSD lifecycle follows only the new toggle.
Estimate/risks: 3–4 h; Fire OS may reject NSD registration and guest Wi-Fi may block multicast; manual address fallback is required.
Commit sequence (each must pass its applicable gate before push):
1. `feat: add expiring one-use TV pairing windows and issue hashed per-phone credentials after pairing`
2. `feat: rate limit PIN attempts and paired device count and authenticate HTTP and WebSocket upgrades`
3. `feat: advertise the enabled service through NSD and add explicit token revocation from TV settings`
4. `test: cover wrong PIN expiry replay and revocation and record day 3 pairing and discovery evidence`
Acceptance: Check off-state advertisement absence; reject invalid PIN, reused session and revoked token. Device gate: two phones discover the same TV, one-time pair, revoke and reconnect without secret logging.
Demo: Phone: discoverable Family TV entry or manual endpoint in a developer client. TV: pairing card with PIN, countdown/window expiry and fingerprint.

## Day 4 — Flutter app skeleton and pairing UI
Goal/features and why: Create the shared app and a clear first-run path; integrate the existing contract rather than creating a second protocol.
Files: `mobile/pubspec.yaml, mobile/pubspec.lock, mobile/lib/{main,app}.dart, mobile/lib/core/{api,storage,discovery}/*, mobile/lib/features/pairing/*, mobile/test/*, docs/PROGRESS.md`.
Launcher connection: Flutter discovery resolves the TV service, pins the displayed fingerprint and uses the Day 3 pairing endpoint. Review plugin permissions and licenses.
Estimate/risks: 3–4 h; Flutter scaffolding is generated code, and iOS plugin minima/secure storage must match targets. Mock demos are clearly labeled.
Commit sequence (each must pass its applicable gate before push):
1. `chore: scaffold pinned Flutter Android and iOS hosts and add shared navigation and accessible app theme`
2. `feat: add typed API errors and protocol models and add certificate pinning and secure credential storage`
3. `feat: add discovered TV list with manual fallback and add PIN entry and first-run connection state`
4. `test: cover pairing and permission-denied UI states and record day 4 analyzer widget tests and pairing demo`
Acceptance: Run flutter analyze, flutter test, existing Java gate and Flutter Android build. Denied LAN permission has a recovery screen; no token appears in logs/preferences. Actual pairing remains pending without a TV.
Demo: Phone: choose a TV, confirm identity, enter PIN and reach the connected screen. TV: pairing card closes on successful pairing.

## Day 5 — Remote pad and foreground-aware controls
Goal/features and why: Give the phone responsive D-pad controls while explaining when the launcher cannot receive navigation.
Files: `launcher/.../lan/{LauncherBridge,EventHub,ActionDispatcher}.java, mobile/lib/features/remote/*, mobile/lib/core/api/events.dart, mobile/test/remote*, docs/PROGRESS.md`.
Launcher connection: Bridge cached lifecycle/focus snapshots into authenticated WS events and route semantic actions through the existing launcher handlers only.
Estimate/risks: 3–4 h; bounded queues, command expiry and disconnect/replay semantics matter more than decorative animations.
Commit sequence (each must pass its applicable gate before push):
1. `feat: expose cached launcher lifecycle and focus snapshots and add bounded authenticated event delivery`
2. `feat: dispatch expiring deduplicated semantic actions and build accessible remote pad with press feedback`
3. `feat: show app-active control limitations clearly and discard stale input and reconnect without replay`
4. `test: cover duplicate OK slow readers and foreground transitions and record day 5 remote control evidence`
Acceptance: Analyze/test both sides; flood queues, disconnect mid-OK and confirm at-most-one dispatch. On stick navigate Family TV; inside another app commands return unavailable and the phone disables the pad.
Demo: Phone: D-pad, OK, Back, Home/Options and connected state; clear message after app launch. TV: existing focus moves only while Family TV is active.

## Day 6 — App launcher grid — lighter day
Goal/features and why: Make installed apps easy to find and open from the phone using cached data.
Files: `launcher/.../lan/{AppsEndpoint,ThumbnailCache}.java, mobile/lib/features/apps/*, mobile/test/apps*, protocol/fixtures/apps*, docs/PROGRESS.md`.
Launcher connection: Serve the cached catalog and bounded thumbnails, then reuse current resolved component/per-app task flags for explicit launch requests.
Estimate/risks: 2–3 h; an app may disappear after catalog load. Never clear data or promise subscription/login verification.
Commit sequence (each must pass its applicable gate before push):
1. `feat: serve paginated installed app snapshots and serve bounded authenticated app thumbnails`
2. `feat: add cached app launch request handling and build searchable phone app grid`
3. `feat: show launch acknowledgement and control handoff and recover from missing hidden or updated app targets`
4. `test: cover pagination launch rejection and grid states and record day 6 app grid demo and evidence`
Acceptance: Unit/widget gates; launch request rejects unknown/hidden packages. Device check each selected tile through phone and verify foreground package; BACK outcome is recorded, not assumed.
Demo: Phone: searchable icons and an Open button. TV: selected installed app opens with its existing account state untouched.

## Day 7 — Android integration checkpoint — lighter day
Goal/features and why: Connect discovery, pairing, pad and app grid into one reproducible Android candidate.
Files: `mobile/integration_test/android_flow_test.dart, mobile/android/*, docs/{SETUP,TESTING,PROGRESS}.md, protocol/fixtures/integration*, release version metadata`.
Launcher connection: Use a fake server for deterministic flows, then a real API 22 device session if available. Keep v1 launcher service opt-in.
Estimate/risks: 2–3 h; physical stick unavailable means release stays an unverified candidate. Never spend the day fabricating acceptance results.
Commit sequence (each must pass its applicable gate before push):
1. `test: add Android end-to-end flow with a fake server and preserve pairing through Android app recreation`
2. `fix: handle Wi-Fi loss and endpoint changes cleanly and cover token revocation and manual reconnect flows`
3. `docs: write reproducible Android install and pairing steps and prepare Android alpha version metadata`
4. `docs: record week 1 acceptance and release gate and record day 7 results and week 2 handoff`
Acceptance: Existing tests plus Flutter analyzer/unit/integration and Android APK build. Verify default OFF, no regression in source/build checks and document any missing physical TLS/NSD/PSS evidence. Tag v0.1.0-alpha.1 only with accurate prerelease notes.
Demo: Phone: complete Android connection-to-launch walkthrough. TV: demo if available; otherwise labeled fake-server walkthrough and explicit pending gates.

## Day 8 — iOS parity
Goal/features and why: Make the same discovery, pairing and control flow work with iOS local-network privacy and secure storage.
Files: `mobile/ios/Runner/Info.plist, mobile/ios/Runner/*, mobile/lib/core/{discovery,storage,api}/*, mobile/integration_test/ios_flow_test.dart, docs/PROGRESS.md`.
Launcher connection: Bonjour resolves the same service and HTTP/WS contract; no ADB or Apple-specific launcher control path.
Estimate/risks: 3–4 h; Xcode download/storage, signing and a physical iPhone may be unavailable. Do not enable global ATS exceptions.
Commit sequence (each must pass its applicable gate before push):
1. `feat: declare iOS local network and Bonjour usage and show permission explanation and manual recovery`
2. `fix: pin local TLS certificates on iOS connections and persist TV credentials through iOS secure storage`
3. `fix: reconnect after iOS background and foreground changes and add iOS parity fixtures and widget coverage`
4. `docs: document simulator and physical iPhone validation and record day 8 iOS evidence and remaining gates`
Acceptance: Flutter analyze/test; flutter build ios --simulator --no-codesign on a compatible Xcode host. Physical device must grant/deny LAN consent and discover/pair; simulator success alone is insufficient.
Demo: Phone: same connected screens on iPhone/simulator with an understandable permission prompt. TV: same pairing/action behavior as Android.

## Day 9 — Rows editor
Goal/features and why: Let the family reorder favorites and move apps between rows safely from either phone platform.
Files: `launcher/.../lan/{RowsEndpoint,RuntimeConfigStore}.java, mobile/lib/features/rows/*, mobile/test/rows*, protocol/fixtures/rows*, docs/PROGRESS.md`.
Launcher connection: Layer atomic runtime overrides over packaged rows.json; validate all changes before swapping the snapshot and preserve focus by package.
Estimate/risks: 3–4 h; two phones can edit simultaneously, requiring revision conflicts and a recoverable unsaved state.
Commit sequence (each must pass its applicable gate before push):
1. `feat: read revisioned launcher row configuration and validate and atomically store row overrides`
2. `feat: apply row updates while preserving package focus and build phone row reorder editor`
3. `feat: add move-between-rows and unassigned app picker and handle conflicting edits without overwriting silently`
4. `test: cover row limits duplicates and interrupted writes and record day 9 row editor acceptance`
Acceptance: Reject >4 rows, >6 apps/row, duplicates and invalid IDs; conflict returns 409; interrupted write preserves old config. Device check reorder while launcher foreground and after returning from an app.
Demo: Phone: drag/reorder and save rows with conflict feedback. TV: the saved row order appears and focus stays on the same installed app when possible.

## Day 10 — Hidden apps editor
Goal/features and why: Allow safe hide/unhide without uninstalling or changing app data.
Files: `launcher/.../lan/{HiddenEndpoint,RuntimeConfigStore}.java, mobile/lib/features/apps/hidden_editor.dart, mobile/test/hidden*, protocol/fixtures/hidden*, docs/PROGRESS.md`.
Launcher connection: Runtime hidden set overlays existing hidden-apps.txt, including Sony LIV; hidden apps remain visible only in the authenticated management list.
Estimate/risks: 3 h; hiding the focused app needs a deterministic focus fallback; concurrent changes must retain revision protection.
Commit sequence (each must pass its applicable gate before push):
1. `feat: expose revisioned hidden app management state and validate and persist hidden app overrides`
2. `fix: remove hidden apps from launchable rows consistently and build phone hide and unhide controls`
3. `feat: show confirmation and reversible hidden changes and preserve a valid selection when the focused app is hidden`
4. `test: cover hide unhide conflicts and default exclusions and record day 10 hidden editor acceptance`
Acceptance: Hide/unhide changes only Family TV; app package/data remain installed. Sony stays hidden unless explicitly unhidden. Hidden app launch requests fail; restore script still works.
Demo: Phone: switches for hidden apps with clear explanation. TV: tiles disappear/reappear without uninstall or login changes.

## Day 11 — Tile and backdrop uploader
Goal/features and why: Make artwork customization visible while keeping decoding and storage bounded.
Files: `launcher/.../lan/{ArtworkEndpoint,UploadValidator,MediaJobs}.java, mobile/lib/features/artwork/*, mobile/test/artwork*, protocol/fixtures/uploads*, docs/PROGRESS.md`.
Launcher connection: Stream authenticated uploads to private temporary files, validate/sample/re-encode on one worker, atomically replace current overrides and invalidate just the affected cache.
Estimate/risks: 3–4 h; compressed image bombs, traversal and low disk space must leave the previous image intact.
Commit sequence (each must pass its applicable gate before push):
1. `feat: stream capped artwork uploads to temporary files and validate image bounds formats and package targets`
2. `feat: sample and atomically replace uploaded artwork and add phone image picker and crop preview`
3. `feat: show upload progress and cancellation and add reset-to-default artwork action`
4. `test: cover oversized corrupt interrupted and traversal uploads and record day 11 artwork demo and memory gate`
Acceptance: Analyze/test; reject invalid magic, >2 MiB, >8 MP, invalid packages and exhausted quota. Device upload during navigation must not decode on main or exceed PSS/performance gate.
Demo: Phone: select/crop image with progress and Reset. TV: new tile/backdrop appears without changing launch behavior.

## Day 12 — TMDB setup and refresh
Goal/features and why: Make optional movie-style backdrops configurable without shipping a shared developer key.
Files: `launcher/.../lan/{ProviderSettingsEndpoint,RefreshEndpoint}.java, mobile/lib/features/artwork/{tmdb_settings,refresh_controller}.dart, mobile/lib/core/api/tmdb_client.dart, docs/PROGRESS.md`.
Launcher connection: TV stores only user-provided private provider state; phone retains its own key in secure storage, fetches over verified HTTPS and fulfills authenticated refresh jobs by uploading artwork.
Estimate/risks: 3–4 h; provider terms/attribution and exact endpoints must be reviewed. A second phone with no key must request setup, never retrieve secrets from TV.
Commit sequence (each must pass its applicable gate before push):
1. `docs: record TMDB terms attribution and key ownership and add write-only provider settings API`
2. `feat: add secure phone TMDB key entry and removal and fetch bounded metadata and image choices on phone`
3. `feat: queue coalesced refresh requests with phone-required state and fulfill refresh jobs through the artwork uploader`
4. `test: cover missing key offline rate limits and refresh cancellation and record day 12 provider acceptance and secret scan`
Acceptance: No key in Git/logs/GET responses, no TLS bypass, one refresh worker, cancel safely. Refresh without a connected foreground phone or local key returns a clear pending/error result. Verify attribution before release.
Demo: Phone: user enters their key, chooses/refreshes a backdrop and sees progress. TV: artwork changes; streaming app accounts remain untouched.

## Day 13 — Status, polish and expanded CI — lighter day
Goal/features and why: Expose useful connection state and make the new mobile/transport checks reproducible.
Files: `launcher/.../lan/StatusEndpoint.java, mobile/lib/features/status/*, mobile/test/status*, .github/workflows/*, tests/*, docs/PROGRESS.md`.
Launcher connection: Status reads cached service/config/job data rather than polling PackageManager or focus state; add Flutter checks to the existing green Java CI.
Estimate/risks: 2–3 h; macOS runner capacity and iOS simulator availability can limit CI. Do not confuse build checks with TV performance measurements.
Commit sequence (each must pass its applicable gate before push):
1. `feat: expose bounded cached status and capability fields and build phone connection and job status screen`
2. `fix: improve large text contrast and disabled control explanations and cover slow clients reconnects and background transitions`
3. `chore: add pinned Flutter analyze and test CI gates and add Android and available iOS simulator build gates`
4. `docs: record enabled disabled PSS and latency evidence or pending status and record day 13 polish and CI results`
Acceptance: CI green for actual configured jobs; fake-server/widget tests cover disconnected, unavailable-control and job-error states. Recheck same-build disabled vs enabled memory/latency on stick, or keep acceptance pending.
Demo: Phone: readable status screen and coherent error/retry states. TV: remote access toggle, matching status and no new focus stalls demonstrated only if hardware is available.

## Day 14 — Release and handoff — lighter day
Goal/features and why: Ship an honestly validated release with installation, restore and demo material.
Files: `README.md, docs/{SETUP,TESTING,PROGRESS,RELEASE}.md, docs/media/*, mobile/pubspec.yaml, launcher version metadata, release tags`.
Launcher connection: Walk through enable, discover, pair, navigate, launch, edit and disable against the documented launcher API; keep rollback and data preservation explicit.
Estimate/risks: 2–3 h; v1.0 is gated on real Fire TV and iPhone acceptance. No fake screenshots or claims; use a clearly labeled prerelease if gates remain open.
Commit sequence (each must pass its applicable gate before push):
1. `docs: write end-to-end Android and iOS setup guide and document API pairing revocation and restore workflow`
2. `docs: add sanitized screenshots of implemented screens and add a labeled demo GIF and capability boundaries`
3. `test: complete release acceptance matrix and secret scan and prepare version metadata for validated release scope`
4. `docs: write release notes known issues and migration guide and record day 14 acceptance and final handoff`
Acceptance: All tests/analyzer/builds and CI green; device matrix covers foreground control limits, default OFF, TLS pairing/revoke, edits/uploads, restart, loss/reconnect and resource gates. Tag v1.0.0 only when these pass; otherwise tag an explicitly unverified prerelease and list blockers.
Demo: Phone + TV: documented end-to-end walkthrough and how to switch the server off. Without hardware show real implemented phone screens with fake-server labeling, not a claimed TV demo.
