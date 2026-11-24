# Development history and verification

These are development observations from November 2026 on one Fire TV Stick 2nd Gen running Fire OS 5.2.9.5 / API 22. They are not claims of compatibility with every Fire TV or phone.

## Work completed

1. Established an external-SSD workspace, Android build/signing path and authorized ADB sideloading.
2. Implemented native launcher and Android companion modules with shared authenticated local commands.
3. Resolved installed app activities through Leanback/Launcher queries; corrected the Prime Video target and retained direct Silk launching.
4. Added launcher-only hidden packages and per-app task flags. Sony LIV was subsequently hidden at user request.
5. Diagnosed Sony LIV's connection error across launcher paths; no launcher-specific network cause was established. No further Sony tests are part of the current pass.
6. Added JioHotstar loading feedback and repeat-launch prevention. Background-process killing was not retained because improvement was not established.
7. Implemented reversible experimental physical HOME handling. Activation failed on the tested firmware; the attempted enablement was undone.
8. Iterated from large glass-style tiles to smaller flat, pre-rendered banners, opaque trays, one small backdrop and short animations.
9. Added configurable lazy rows and startup discovery, preserving launch routing and user data.

## Latest complete tile launch pass

Each visible tile was activated once using ADB D-pad/OK events. `dumpsys activity activities` verified the foreground package. One BACK press was sampled after launch, not after exhaustive navigation to an app's root. No login or playback operation was performed.

| App | Correct package foreground | Single BACK result |
|---|---|---|
| Netflix | Yes | Stayed in app |
| Prime Video | Yes | Stayed in app |
| YouTube | Yes | Stayed in app |
| JioHotstar | Yes | Stayed in app |
| JioCinema | Yes | Stayed in app |
| Appstore | Yes | Family TV |
| Downloads | Yes | Family TV |
| Silk | Yes | Stayed in app |
| Screensaver | Yes | Family TV |
| System Status Monitor | Yes | Family TV |

ZEE5 and discovery+ were not found during that startup. Sony LIV was hidden and not launched. Existing Prime/YouTube accounts were reported working by the device owner earlier; activity inspection alone does not prove signed-in playback. Netflix subscription playback was not tested. Family-wide phone compatibility has not been validated.

## Performance evidence

An earlier glass UI snapshot reported 51,274 KiB PSS (50.1 MiB). A later flattened-banner snapshot reported 33,544 KiB (32.8 MiB). These are separate snapshots, not a controlled memory benchmark, and neither is a fresh measurement of the final configurable-row build. Ten rapid RIGHT events were sent in the flattened-banner pass. Gfxinfo/compositor output did not provide usable per-frame duration evidence, so median/worst frame time, sub-150 ms focus latency and a 33 ms frame budget are **not verified**.

The historical [phase-2 audit](../phase-2-launch-audit.md) covers an earlier build and includes Sony LIV before it was hidden. Warm/cold app samples there are not directly comparable and do not prove a speedup over stock Home.

## Open work

- Reliable, supported HOME takeover on this firmware is unresolved.
- Universal BACK return is not implemented.
- The unseen-row peek is implemented and checked on the Mac; TV verification is pending. Roughly 4.5 visible tiles remains incomplete.
- Focus-path allocations, row creation work and container flattening remain.
- Generic installed-video classification is incomplete beyond configured packages and ZEE5/discovery label matching.
- Repeat-aware animation skipping is implemented locally; device verification and focused-animation-only hardware layer control remain pending.
- The phone remote is not a system-wide controller; volume/power support is absent.
- Build identifiers remain launcher 0.8 and remote 0.2 across several development iterations; they are not published release milestones.

Raw device logs, serials, local addresses, signing material and personal screenshots are intentionally excluded from this public repository.

## Next-row preview update — November 13, 2026

Implemented a preview for rows that have not been opened yet. Each preview uses a single non-interactive View sharing pre-rendered tile bitmaps. The focused row remains in the bottom slot; previous rows fade away, and the next row appears at half opacity. Full interactive rows remain lazy. No package discovery or bitmap decoding was added to focus handling.

Local validation: `JAVA_HOME="$(/usr/libexec/java_home -v 17)" sh tests/check-rows.sh` checks one through four rows, DOWN/UP state transitions, the focused row position, previous-row visibility and approximately 25% next-tile exposure. `sh build-launcher.sh` builds the APK and verifies its development signature. These checks validate placement rules and compilation, not Android rendering or performance.

Device acceptance check, when the TV is available: confirm the initial next-row peek; navigate DOWN/UP through every configured row; confirm no preview remains after the last row, no double drawing when revisiting a built row, and that the backdrop stays visible. Repeat with one row and four rows. On-TV verification is pending; no new device or frame-time results are claimed.

## Navigation and launch-overhead update — November 13, 2026

Fast individual direction presses (under 100 ms apart) and repeated held-key events snap focus scale and row scrolling to their target. Normal moves use 100 ms animations. Horizontal navigation no longer restarts row-visibility animations; focus reuses the existing interpolator and precomputed package string. This removes avoidable work but does not establish an end-to-end input latency.

Launch intents and per-app flags are prepared on the startup artwork worker. The resolved component and launch category are retained; Silk keeps its direct browser route. Launch logging uses the cached component rather than a PackageManager lookup. Click dispatch now uses a reusable callback after one scheduled feedback frame, replacing two nested frame callbacks. Loading feedback, launch debounce, app task flags and BACK behavior are preserved.

Backdrop requests invalidate old results as soon as focus changes. Pending updates and neighbour prefetch stop when the launcher pauses, then resume after return; a decode already running may finish. This avoids scheduling decorative work during another app's startup. No force-stopping, data clearing or persistent service was added.

Local checks: `JAVA_HOME="$(/usr/libexec/java_home -v 17)" sh tests/check-navigation.sh` exercises first press, fast taps, held-key delay, normal input, old timestamps and recovery after idle. Row checks and the signed APK build also passed. No TV was connected: app launch/BACK checks, animation appearance, PSS and latency measurements remain pending. First navigation to an unbuilt row still creates its Views on the UI thread. Streaming apps control their own cold-start and exit times; this update cannot guarantee faster app closing.

## Remembered row focus update — November 13, 2026

Added session-only selected columns per row and restoration of the saved app in a lazy lower row. Previously DOWN/UP always chose the first tile, and a saved lower-row key fell back to Favorites because its controls had not been created.

`sh tests/check-rows.sh` now also tests round-trip row selection, first visits to shorter rows, lookup of a saved app in an unopened row, reordered catalogs, removed/hidden keys, null saved focus and invalid selections. Existing row geometry and rapid-navigation checks pass. The direct launcher build and APK signature verification pass. These are Mac-side logic/build checks; Android focus, scroll position and BACK/launch integration still require the TV.

TV acceptance: choose the fourth Favorite, enter Tools, choose its second tile, then move UP/DOWN and confirm both selections return. Restart while a lower-row tile was last focused and confirm that row opens with the saved tile. Remove/hide the saved tile in a test configuration and verify the Favorites fallback. No new device latency, memory or playback results are claimed.

## UI and reliability review — November 14, 2026

Reviewed the launcher Activity/catalog/artwork classes, row helpers, experimental HOME service, phone companion, local server, shared protocol, manifests, build and restore scripts. The changes address concrete findings; this is not an independent security audit or proof that every bug is fixed.

| Finding | Change |
|---|---|
| Empty installed/visible catalog indexed row zero | Show a readable empty state and retain Settings access |
| Malformed later row could discard already consumed catalog entries | Parse row definitions before consuming the catalog |
| App update/removal during startup could abort artwork setup | Isolate per-app runtime failures and skip that entry |
| Tile constructor created and discarded an extra Button | Dedicated TileButton initializes one control; reusable white focus ring improves visibility |
| Settings focus allocated a new decorative drawable | Reuse two opaque surfaces with a clear outline |
| Every startup regenerated 1280×480 JPEG backdrops | Generate 640×240 and reuse a private disk cache when sampled icon fingerprint/color match |
| In-flight worker could refill an evicted cache during destruction | Queue final eviction after artwork work, release the displayed image |
| IPv4 validation accepted a trailing dot | Strict validation preserving empty octets |
| Rejected pairing request left pairing permanently busy | Reset pairing state on queue rejection |
| Slow connections could accumulate outdated directional commands | Drop queued commands older than 750 ms before connecting; show a retry message |
| Forget/address change could leave queued work targeting the old TV | Clear queued work, invalidate callback versions and cancel pending Home tap on address change |
| A previous server session could deliver a callback into a later session | Guard launcher callbacks with a session identifier |

Checks: existing row/selection/navigation checks; new `sh tests/check-review.sh` for strict IPv4 cases, stale-command and endpoint-version boundaries, HMAC known vector, proof comparison, and nonce/action/phone-key separation. Both APKs compile and their signatures verify. The protocol wire format, task flags, hidden list, row membership, BACK handlers and restore script are unchanged. No TV or emulator run occurred: focus rendering, lifecycle behavior, pairing UX, PSS and latency require device verification. Cache/UI changes have source/build review only, not Android graphics tests.

Performance scope: this removes duplicate View construction and repeated artwork generation. It does not establish a measured improvement on the stick. Already executing network exchanges cannot be revoked by forgetting a TV; only queued work and stale UI callbacks are invalidated. Shared-network authentication still has the limitations described in ARCHITECTURE.md. Lazy row creation on first access and some focus-path allocations remain.

## Week 1 Android alpha — November 24, 2026

Days 6–7 added cached phone app browsing/launches and integrated Android lifecycle/recovery. [Week 1 evidence](WEEK_1_ALPHA.md) records the host-only walkthrough, actual pinned Dart ↔ Java TLS/WSS integration, 31 phone tests, native/protocol checks and APK verification. Physical Android storage/discovery, Fire OS app launches/BACK, PSS and latency remain unverified. Historical TV captures above are not screenshots of this candidate. Issues #11/#12 remain open for those gates.

## Days 8–9 — November 24, 2026

[Day 8](DAY_8_IOS.md) records iPhone recovery and mocked secure-store/config checks; Xcode/native validation is blocked. [Day 9](DAY_9_ROWS.md) records revisioned atomic row overrides and phone editing/conflict/lifecycle coverage. Passed locally: analyzer/44 phone tests, actual pinned Dart/Java TLS/WSS row/app integration, native/protocol gates and signed TV/phone builds. Fake storage and host UI do not substitute for actual Keychain, Android AtomicFile crash or TV focus/memory/latency tests. No new device captures or performance numbers were produced.

## Day 10 — November 24, 2026

See DAY_10_HIDDEN.md for reversible phone visibility editing, default/Sony confirmation, hidden launch rejection, conflict and background recovery. Passed: analyzer; full phone baseline plus seven hidden cases (51 distinct); actual pinned Dart/Java TLS hide/unhide/replay; native/protocol gates; signed TV/Android dev APKs. Fake storage and host UI do not establish actual focus fallback, queued-key timing, crash recovery, account continuity, PSS or latency; physical tests remain pending.
