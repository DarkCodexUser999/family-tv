# Family TV v0.6 launch audit

Fire TV Stick 2nd Gen, Fire OS 5.2.9.5 (API 22). Grid trials used real ADB D-pad and OK events. Foreground activity is from Activity Manager. One Back press was sampled per trial; it may be handled by the app's own screen. First-frame values are Activity Manager's launch-time samples, not elapsed time from remote press. A dash means the system emitted no new frame event.

| Tile | Foreground | First frame, three runs (ms) | One Back press |
|---|---:|---|---|
| Appstore | 3/3 | —, —, 493 | Family TV 3/3 |
| Downloads | 3/3 | 495, 160, 166 | Family TV 3/3 |
| JioCinema | 3/3 | —, —, 930 | Family TV 2/3; stayed in app once |
| JioHotstar | 3/3 | —, —, — | Stayed in app twice; profile picker once |
| Netflix | 3/3 | —, 618, 603 | Stayed in app 3/3 |
| Prime Video | 3/3 | —, —, 378 | Back during routing left Prime's landing screen visible |
| Screensaver | 3/3 | —, 386, 278 | Family TV 3/3 |
| Silk Browser | 3/3 | —, —, — | Family TV 2/3; Amazon tutorial once |
| Sony LIV | 3/3 (splash only) | —, 250, 530 | Family TV 2/3; stayed on splash once |
| System Status Monitor | 3/3 | —, 473, 196 | Family TV 3/3 |
| YouTube | 3/3 | —, 356, 205 | Stayed in app 3/3 |

| Metric | v0.5 | v0.6 |
|---|---|---|
| Family TV initial launch, `am start -W` ThisTime | 2,890 ms (TotalTime 49,051 ms) | 1,911 ms (TotalTime 1,911 ms) |
| JioHotstar first frame, targeted trial | 5,060 ms, new activity | 412 ms, reused task |
| Sony LIV targeted trial | Correct splash activity requested; closed itself about 2.6 s after creation; no first-frame event | 1,760 ms to splash; still on splash after the observation window |
| Family TV foreground PSS sample | 36,851 KiB | 19,850 KiB |
| Launcher resume after Back | Earlier close-to-resume samples: Downloads 49 ms, Appstore 113–412 ms | Foreground return recorded; comparable device intervals not collected |
| Tile focus latency | No comparable key-to-frame measurement | No comparable key-to-frame measurement |
| Gfxinfo | 34 views in prior capture | 34 views, 41.91 KiB of display lists; no usable frame timing rows |
| Warm Amazon Home vs. Family TV | No comparable v0.5 sample | Amazon Home 5,388 ms; Family TV 6,340 ms (single `ThisTime` samples) |

These are spot samples under different process and cache conditions. They do not establish a speedup over stock Home or isolate the effect of launch flags. The launcher does not clear package data or verify account or playback state. No background-app killing was enabled.

## Direct ADB launches

These `am start -W` samples bypassed Family TV and its task routing. A dash means no matching completion within the per-command timeout. No equivalent per-app v0.5 direct-launch baseline was captured.

| App | v0.6 ThisTime (ms) |
|---|---:|
| Appstore | 6,729 |
| Downloads | 5,265 |
| JioCinema | 7,843 |
| JioHotstar | — |
| Netflix | — |
| Prime Video | — |
| Screensaver | — |
| Silk Browser | — |
| Sony LIV | 707 (splash) |
| System Status Monitor | 368 |
| YouTube | — |
