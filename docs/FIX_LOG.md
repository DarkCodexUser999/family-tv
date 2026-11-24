# Family TV fix log

## Batch 1 — 19 November 2026 — issue #6

Scope: F04, F05, F08 and F09 from review 923be34 only. Existing local changes were reviewed and corrected in the original checkout; untracked source was read explicitly. No reset, clean, stash, wholesale source regeneration or system-package operation was used. Index exports isolate the exact commit candidates without replacing the working files. The pre-push rebase check for the first commit used its clean export because the remaining Batch 1 changes stayed unstaged.

| Finding | Delivered behavior | Evidence and limits |
|---|---|---|
| F05 | Discovered final component/category first, package task flags last; no second Silk lookup. Prime app preferred over player only when present. | Executable production route/intent construction under host Android fakes: default flags, own-task flags including Silk/Hotstar, component/category, Prime alias/player-only, missing component. Executable production dispatch catches ActivityNotFoundException after discovery and SecurityException, releases pending launch and presents failure. Real Android task behavior UNVERIFIED. |
| F04 | Gear and MENU open local options; explicit Fire TV Settings has opening feedback, resolved-component logging, duplicate-dispatch guard and cancellation on pause/destroy. Gear participates in saved focus; pairing/revocation options preserved. | Production Settings/dispatch methods execute under host fakes: duplicate presses, dialog dismissal, focus request, paused/destroyed dispatch, missing handler and failure recovery. Saved state, callback cancellation, gear/MENU wiring and retained LAN actions are separately labeled SOURCE checks. Real dialog lifecycle, Activity recreation, resolved Fire OS component/task, first physical BACK and return focus UNVERIFIED. |
| F09 | Centralized tile/backdrop lookup and bounded decode; missing/denied/corrupt art falls back, private backdrop preparation failure cannot discard a valid tile; failed backdrop completion clears stale image. Override priority preserved; no per-package shared README/directory writes. | Executable production lookup/decode tests under fake Environment/BitmapFactory cover absent files, denied storage, malformed/failed decode, JPG-before-PNG and sampling. Actual production shelf completion executes with a failed loader and clears the previous image. SOURCE checks confirm tile fallback wiring and no shared README writes. Real storage permissions/rendering UNVERIFIED. |
| F08 | Hide-read failure merges all bundled defaults even after a partial read, including Sony LIV; discovery exception provides gear/local Settings and Retry. | Executable production hide reader under fake assets covers successful custom list, initial failure and partial-read recovery. CI asset checks validate package syntax, duplicates, own-task lists, row structure and exact fallback parity. Catalog failure/retry UI wiring is a SOURCE check; actual Android retry UI UNVERIFIED. Live package refresh excluded. |

### Actual checks

- Full `sh tests/check-all.sh` passes here, including real local TLS, pairing, bridge, WSS and discovery tests. The previous sandbox bind failure was rechecked without weakening/skipping tests; socket binding succeeds with the authorized host execution.
- New executable host tests compile the production AppCatalog, ArtworkFiles and LaunchRoute classes against explicit Android fakes; Settings/dispatch/shelf methods are extracted verbatim from production into isolated host harnesses. These establish method behavior, not Android framework lifecycle or rendering. AppleDouble `._` metadata is excluded from generated fixture compilation.
- Row presentation/selection memory, navigation, remote input and HMAC regression checks pass. Loading/one-pending-launch, local-menu routes, callback removal and LAN menu preservation also have labeled SOURCE checks. No new claim of physical Hotstar/streaming-app or BACK acceptance.
- Protocol: 9 positive/20 negative fixtures plus actual WSS state/ACK validation pass.
- Flutter analyzer clean; all 21 mobile tests and the real pinned Dart-to-Java interoperability test pass using the existing SSD-backed toolchain. No Flutter source or dependency change.
- Each exact staged candidate passes the full host gate, protocol checks and `sh build-launcher.sh` including v1/v2/v3 signature verification, before commit. API 22 remains the minimum; signing configuration/identity was not replaced. No install attempted.
- `../platform-tools/adb devices -l` returns no devices. Device component, task ID, first physical BACK, gear return/recreation, permissions/rendering, installed signing identity and latency/PSS: **UNVERIFIED**. No device/system package or app data changed.

### Publication

Issue: https://github.com/DarkCodexUser999/family-tv/issues/6.
First commit: db161e4 — final-component-first launch routing with executable route/intent tests.
The final recovery/tests/documentation commit closes #6 for the Batch 1 host-delivered scope; physical checks remain explicitly UNVERIFIED. Final remote equality, leftover files and issue state are verified after pushing. No Batch 2 changes are included.

## Batch 2 · 20 November 2026 · source publication, physical acceptance pending · issue #7

Baseline bfce666. Commit 1f5cc8e adds timing instrumentation while retaining the original navigation and artwork behavior. Verified publication artifacts are saved outside Git in ../tools/batch2-publication/: baseline-standard.apk, baseline-timing.apk, optimized-standard.apk and optimized-timing.apk, with SHA-256 and signer receipts in published.json. Astra’s earlier batch2-timing-baseline.apk and batch2-timing-after.apk under launcher/build/local are retained unchanged as historical artifacts. Timing APKs are debuggable; standard APKs are not debuggable and leave tracing inactive. All builds retain API 22 and the existing signing key; signer equality is checked. No dependency added.

| Change | Implementation and local evidence |
|---|---|
| F02 | Initial row capped at five controls. A main-queue idle callback prepares at most one additional Button per animation tick. A seven-column window bounds each row, retaining the first favorite and any focused or pending-launch control when necessary. Replaced previews are detached. Directional dispatch records a pending destination when its view is absent. A JVM harness executes the production navigation methods over two 100-item rows, including 45 successive directional presses before preparation, deferred OK, crossing many windows and restored row selection. |
| F01 | Production selection snaps the outline, tile scale, row position and opacity. Debug mode also offers a single 60 ms arriving-tile scale via the boolean Activity launch extra short-focus-transform. Repeats snap. No departing-tile animation or strip/row fades. |
| F03 | Art waits for the next animation callback. A warm cache hit updates the image directly. One worker decode may finish while selection changes; only the latest requested image can be applied. Speculative prefetch was removed. A host harness blocks the first decode, changes selection twice and checks that the intermediate selection never starts and a warm hit needs no worker. |

### Physical comparison

| Measurement | Timing-only baseline | Batch 2 snap | Batch 2 single transform |
|---|---|---|---|
| Key to outline, median / p95 | UNVERIFIED | UNVERIFIED | UNVERIFIED |
| Key to art and outline to art, median / p95 | UNVERIFIED | UNVERIFIED | UNVERIFIED |
| Frame time, median / p95 | UNVERIFIED | UNVERIFIED | UNVERIFIED |
| Attached view count and process PSS | UNVERIFIED | UNVERIFIED | UNVERIFIED |

At handoff ADB could not open its listener. In this completion session ADB starts successfully, but adb devices -l returns an empty list. No installation, remote presses, timing samples, task observations or live memory measurements were possible. For all three variants use identical catalog, screen and starting positions. Record at least ten successful transitions per direction, separating first/revisited rows and isolated taps/held repeats. For first-visit samples restart the launcher between trials. FamilyTVTiming debug logs use KeyEvent uptime and the matching ring/shelf draw callbacks; these approximate draw submission. Record on-screen presentation separately if available. Group final nonnegative fields by sample ID and condition; report superseded or missing samples separately. Collect dumpsys gfxinfo and meminfo without resets; report unavailable frame data explicitly. Targets of about 100 ms to the outline and 33 ms per frame remain unmeasured. No bottleneck ranking or speedup can be inferred from host tests.

### Completion checks and publication

- The earlier sandbox loopback bind and GitHub DNS blockers were rechecked in the authorized host environment. The full real LAN gate passes; no test was skipped or weakened. GitHub fetch, issue creation and publication work.
- Each exact index candidate is exported for isolated verification while preserving the original checkout and all pending work. Both candidates pass the complete check-all.sh gate, protocol fixtures (9 positive/20 negative plus actual WSS state/ACK), and standard/timing launcher builds with signature verification. Each APK's debuggable flag and min API 22 are checked; signer SHA-256 equality is enforced across variants. No stash, reset or clean operation is used.
- Executable host tests: route/intent policy; Settings/recovery; partial hide-list recovery; stale/latest-only backdrop completion and immediate cache hit; cold-row navigation and deferred OK across two 100-item rows; view-window bounds and selection memory; existing input/HMAC/rows/LAN/pairing/bridge/discovery checks. Trace tests use a synthetic clock to check sample IDs, repeat metadata, draw matching and missing fields; those deltas are not device measurements. Android layout/lifecycle scheduling remains UNVERIFIED.
- Mobile regressions run against the completed working tree: scripts/check-mobile.sh has clean analysis and all 21 tests pass; scripts/check-remote-interop.sh passes real pinned Dart-to-Java WSS interoperability. Flutter source/dependencies and LAN production code are unchanged.
- Independent read-only review found no concrete correctness blocker in the existing optimization. No additional feature batch, TMDB, route change, hide-list change, system-package operation or physical speedup claim was added.
- Issue: https://github.com/DarkCodexUser999/family-tv/issues/7. Source is delivered in two tested commits: 1f5cc8e (timing-only baseline) and the accompanying optimized navigation/artwork commit. Published commit and actual CI links are recorded on the project card after push. Issue #7 stays OPEN/Blocked for the physical table above; publishing code does not establish the two-second-lag fix or the 100 ms outline target.

## Batch 3 · 20 November 2026 · source publication; device acceptance pending · issue #8

Baseline d5b6952. The TV APK uses native Views, BitmapFactory, platform HttpURLConnection and the existing SDK. Added runtime dependencies: none.

| Finding | Implementation | Evidence |
|---|---|---|
| F06 | One 8 MiB decoded-art cache counts allocation bytes across tiles and stills, with a three-backdrop ceiling. A 1280×720 RGB565 frame uses 1.76 MiB. The visible row and its peek load before hidden controls. Catalog entries retain metadata, hidden rows release drawable references, and trim callbacks evict the cache. Workers use application context for file/package access, check destruction before publishing and serialize pixel decodes across Activity recreation. | Production cache under host bitmap fakes passed byte eviction, entry replacement, backdrop count and trim checks. Displayed images, cross-fade and temporary decode allocations are additional to cache bytes. |
| F07 | Removed the generated magnified-logo shelf. Local overrides have priority. With a credential and connectivity the TV resolves provider IDs separately for movies and TV via the India provider-list endpoints. Discover uses watch_region=IN, a 90-day release/first-air interval and popularity ordering. Up to five distinct usable stills per provider rotate. Downloads request w1280, validate JPEG markers and dimensions, and rename completed files into a private cache capped at 30 JPEGs. Refresh attempts are persisted at most once daily. Missing, unmapped or corrupt art uses a dark tinted background. A short alpha transition is skipped during rapid navigation. Tiles use rounded, pre-rendered local overrides, aspect-preserving TV banners or full-height centered icons with edge-color/gradient fill. | APK compiled and verified. Existing host tests cover invalid decodes, stale completion clearing, blocked-worker/latest-only behavior, warm cache and focus regressions. Live TMDB provider matches, Fire OS TLS and physical appearance are UNVERIFIED. |
| F14 | The optional file /sdcard/FamilyTV/tmdb_key.txt supplies a v3 key or read token. The client uses fixed HTTPS hosts, disables redirects, keeps credentials off image requests and suppresses provider exception details. The status endpoint reports a boolean. README includes the requested attribution. The pre-commit/CI suite scans tracked content and sensitive filenames using synthetic positive and negative checks. | Credential filename ignores, synthetic scan and protocol-fixture checks passed. No real key was requested, printed or used in this session. |

### Device acceptance and publication

| Check | Result |
|---|---|
| Row 1 HD art screenshot, row 2 full-height tiles and offline fallback screenshot | UNVERIFIED |
| Before/after launcher PSS; focus latency versus Batch 2 | UNVERIFIED |
| Real India provider IDs returned for the installed apps, live refresh and 4 m legibility | UNVERIFIED |

At handoff the sandbox blocked the ADB listener, LAN loopback bind and GitHub DNS. These blockers were rechecked on the authorized host: the full unskipped check-all gate (including real TLS/WSS) passes and GitHub fetch/issue creation work. ADB starts, but devices -l is empty; no installation or device data change occurred. The signed launcher is built and verified before publication. Protocol fixtures and mobile analyzer/21 tests/real Dart–Java interop are checked using the existing toolchain. The complete staged integration is tested and published as one cohesive cache/provider/UI increment because its classes and view wiring depend on each other. The secret scan runs after all new source is staged. No real credential was used and live TMDB/API22 results remain UNVERIFIED. Issue #8 stays open for physical acceptance: https://github.com/DarkCodexUser999/family-tv/issues/8. Final commit/CI evidence is recorded on the project board; no Batch 4 work is included.

## Batch 4 · LAN control, restore and launcher cleanup

| Finding | Change | Verification |
|---|---|---|
| F10 | Connectivity updates coalesce. Pairing reports a busy queue. Revocation records a pending request and reports its result in Family TV settings. | The blocked-worker test covers ordering and durable revocation outcomes. The full suite passed on the project owner's Mac, including real loopback TLS and WSS. |
| F11 | Restore recognizes both legacy HOME service names, including after an uninstall. It validates ADB reads, checks the current list before updating it and verifies resulting settings, grant and package state. | Mock ADB covers stale, mixed and null lists, denied reads, concurrent edits, interrupted writes and repeat runs. |
| F12 | Removed the HOME accessibility service and XML, unused backdrop and tile views, and the launcher's legacy TCP server and pairing UI. The native phone companion still builds. Restore and the explicit legacy-forget action remain available for old installations. | The integrated host suite exited 0. OpenAPI and event-schema checks passed for 9 positive and 20 negative fixtures, including validation of the available WSS state and acknowledgment output. |

Both native APKs built and passed signature verification at each of the five prepared code revisions.

Physical Fire OS behavior and incremental PSS with LAN enabled are **UNVERIFIED**. The ADB daemon could not open its local listener in this session.

Published code commits for issue #9

- c9e77c7825ae fix: preserve LAN command ordering and report revocation outcomes
- 8830ac533f55 fix: verify legacy HOME cleanup and restore operations
- 9e6d2500e4b9 refactor: remove the experimental HOME accessibility service
- 0d8f327700a5 refactor: remove unused launcher backdrop and tile views
- 4d037971ea27 refactor: retire the legacy TV pairing server

Each listed commit passed its full host suite, protocol checks and both signed native APK builds before publication.

## Batch 5 · Launcher ownership, build parity and mobile CI

| Finding | Change | Evidence |
|---|---|---|
| F13 | Catalog entries snapshot labels and launch routes. Plain Java assigns configured and fallback rows. The Activity owns artwork loading and releases its workers and cache on destruction. Android focus, dialogs and dispatch stay in the UI. | Host tests cover immutable snapshots, own-task routes, missing and duplicate packages, row order, empty rows, rapid navigation, shelf replacement and late cache writes after shutdown. |
| F15 | Both native projects target Java 8 bytecode under JDK 17. The build checks Gradle and shell settings and inspects the actual APK manifests and compiled classes. The launcher uses D8. | Both native APKs built and verified with certificate SHA-256 digest `619c734040f18e5c44da2b994efc5c726073dce65efcbcd927b7f8c9abb809c1`, matching Batch 2. The TV APK targets API 36, supports API 22, and contains no Flutter assets or native libraries. The shrink-only measurement and signing policy are in [Setup](SETUP.md). |
| F16 | CI runs mocked restore scenarios, Flutter analysis and tests, required Dart/Java interoperability and a phone debug build. The README labels the two November 10 Fire TV screenshots and links the Flutter host render separately. | Both historical PNGs match the original 1920 × 1080 device captures byte for byte. The workflow YAML and local media links validated. |

During local preparation the catalog, restore, navigation, cache, LAN ordering, pairing/bridge, discovery and protocol fixture checks passed. The integrated suite stopped at the first real TLS socket because the sandbox denied the bind. Flutter's SDK disk image could not mount here. Current physical Fire OS behavior, timing and memory use remain **UNVERIFIED**. ADB could not start its local listener.


### Batch 5 publication completion — 21 November 2026

Issue #10: https://github.com/DarkCodexUser999/family-tv/issues/10. The prior sandbox socket, SDK mount and GitHub access blockers were resolved on the owner’s Mac. Four prepared commits passed their required gates and were pushed:

- 1432ca3 refactor: separate catalog rules and artwork lifetime from the launcher UI
- 12a39af build: verify native package settings and compiled artifacts
- 19a5ac4 ci: exercise the Flutter companion and real Java transport
- 8611665 docs: show historical Fire TV captures alongside labeled host evidence

All four passed full host/protocol checks and both signed native APK builds. The CI and screenshot revisions also passed Flutter analysis, 21 tests, required real Dart–Java interoperability, the phone debug APK build and lockfile verification. The owner then requested stopping repeated checks; this final log uses those completed results, with no further feature testing or builds. Physical Fire OS behavior, timing and PSS remain UNVERIFIED. Historical screenshots are not current device acceptance. No further batch started.

## Days 6–7 integration — 2026-11-24

Added authenticated bounded app pages/icons and revision-bound explicit launches using existing tile callbacks, plus the Flutter searchable app grid. Queue tests enforce one in-flight request, stale/hidden rejection, lifecycle/revocation rechecks, expiry and duplicate suppression. Fixed malformed short app paths so missing package IDs return an error without an escaped worker exception. Package events invalidate the snapshot; thumbnails prepare after home construction with remote access enabled only. Launch routes, hidden configuration, restore and external-app BACK logic were preserved.

The integrated host walkthrough exposed a covered remote screen reconnecting on phone resume; it now stays suspended until the app grid is popped. Grid resume requires manual Refresh and never replays an uncertain launch. Saved-store recreation and changed-address recovery are covered by host fakes; actual secure storage/Android recreation remain pending.

Passed: 31 phone tests/analyzer, native regression gate (70 TLS + 10 explicit-launch queue checks), real production Dart/Java TLS/WSS app/remote interop, protocol validation, signed launcher and Android alpha builds. See WEEK_1_ALPHA.md and PROGRESS.md. No TV/phone/emulator was used, no PSS/latency figure is claimed, and no iOS/next-day feature was implemented.

## Days 8–9 — 2026-11-24

Day 8: inspected the pinned iOS discovery plugin's generic native-error mapping; added honest Local Network guidance and explicit retry instructions. Production secure-store channel arguments/read-back/locked/corrupt handling pass with a mocked native channel; iOS plist/Bonjour/ATS/entitlement guards pass. Full Xcode is absent, so no simulator/native or actual Keychain/consent claim is made.

Day 9: added revisioned cached row reads and bounded authenticated PUTs, private AtomicFile overrides, strict bounds/catalog checks, serialized revision conflicts and exact save responses. Row application reuses existing package-focus restoration and defers rebuilds while busy/backgrounded. Phone editor handles ordering/moving/picking/unassigning, explicit Save, dirty-discard confirmation and draft retention on conflict/uncertain save; returning refreshes the grid. Failed metadata refreshes cannot enable editing from stale data.

Passed: 44 phone tests/analyzer, production Dart/Java row/app TLS/WSS interop, full native suite including 77 TLS and 22 fake-disk row cases, protocol validation, API 22 launcher build/signatures and one updated Android dev APK build (209.3 s, code 3/API 26–36, v2 signature verified). Restore, per-app task flags and the hidden list remain unchanged. Physical Android AtomicFile crash recovery, TV row/focus behavior, PSS/latency and native iOS remain unverified. See DAY_8_IOS.md, DAY_9_ROWS.md and PROGRESS.md.
