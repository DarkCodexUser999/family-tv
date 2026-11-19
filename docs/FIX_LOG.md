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
