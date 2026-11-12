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
- Initial unseen-row peek and roughly 4.5 visible tiles are incomplete.
- Focus-path allocations, row creation work and container flattening remain.
- Generic installed-video classification is incomplete beyond configured packages and ZEE5/discovery label matching.
- Repeat-aware animation skipping and focused-animation-only hardware layer control are unfinished.
- The phone remote is not a system-wide controller; volume/power support is absent.
- Build identifiers remain launcher 0.8 and remote 0.2 across several development iterations; they are not published release milestones.

Raw device logs, serials, local addresses, signing material and personal screenshots are intentionally excluded from this public repository.
