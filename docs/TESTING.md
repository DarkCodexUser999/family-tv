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
