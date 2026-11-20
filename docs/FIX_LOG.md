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
