# family-tv — project tracker

Created 2026-11-15. The live public board tracks these milestones; the repository documents its rules and historical evidence.
Live public board: https://github.com/users/DarkCodexUser999/projects/1
Repository: https://github.com/DarkCodexUser999/family-tv

## Board setup

Name: **family-tv**. Public visibility was explicitly approved by the owner.
Columns: **Backlog**, **Ready**, **In progress**, **Blocked**, **Done**.
Each card: goal, source/issue links, actual result, validation level, next action.
“Done” means that card's stated scope is complete; host-tested code never implies physical TV acceptance.
Use existing issue #1 for Day 1 rather than inventing retrospective issues or commits.

## Backfill — completed work

Development before the first snapshot is summarized from docs/TESTING.md and phase-2-launch-audit.md; do not invent individual commit dates for it.

| Card | Result/evidence | Validation |
|---|---|---|
| Establish external-SSD workspace and Fire TV compatibility | API 22 Java build, ADB setup, signing and restore path; initial snapshot 4260196 | Historical device/build evidence in TESTING.md |
| Native Family TV launcher and app launch audit | Installed Leanback/Launcher resolution, Prime/Silk routes, catalog and existing logins preserved; 4260196 | Historical package foreground checks; BACK varies by app |
| Local Android companion MVP | Existing `/remote` and HMAC protocol `/shared`; 4260196 | Source/build baseline; family phone coverage and system-wide control not established |
| Hidden apps and task routing | Editable hidden list, Sony LIV exclusion, per-app task configuration, Hotstar loading feedback; 4260196 | Historical launcher trials; no app-data clearing |
| Flat TV home and configurable rows | Banner tiles, small backdrop, D-pad layout and lazy row configuration; 4260196 | Historical screenshot/launch checks; further polish pending |
| Next-row preview and navigation overhead | 9edda16 | Mac regression/build checks; TV validation pending |
| Remember row selections and restore lazy-row focus | 1a9cc32 | Mac regression/build checks; TV validation pending |
| Startup/UI/remote reliability fixes | 26587fe | Regression checks and both APK builds; TV/phone lifecycle validation pending |
| Automated GitHub tests and dual APK builds | 095503f; Linux shell correction 3d5549c | Actual GitHub CI success; not a device test |
| Flutter remote architecture and fourteen-day plan | 6a23546, 73c2598, e536f3a, 9986648 | Planning complete; not an implemented server/app |
| Day 1 protocol and toolchain baseline | Issue #1; f978d6d, 547d870, 4063618, 6608908 | 27 fixtures, schema checks, existing regressions and both APK builds; CI success |

Commit links use https://github.com/DarkCodexUser999/family-tv/commit/<hash>.
Issue #1: https://github.com/DarkCodexUser999/family-tv/issues/1 (closed).
Day 1 CI: https://github.com/DarkCodexUser999/family-tv/actions/runs/43675110742 (success).

## Blocked on device acceptance — Days 2–5 code published

- **Day 2: tiny opt-in launcher server.** Published after approval; host checks/CI passed. Physical TLS/PSS/latency gates remain open, issue #2.

- Day 3: pairing/discovery approved and published on 2026-11-17 (f13f5b6 (pairing core), 0295e50 (bounded TLS/auth), 156fbb2 (discovery/TV settings)). Physical acceptance pending, issue #3. See docs/DAY_3_REVIEW.md; final CI links are on the live project card.

- Day 4: Flutter pairing source approved and published; local analyzer/tests/three APK builds passed. Physical validation pending. Issue #4 and docs/DAY_4_REVIEW.md.

## Backlog — planned, not completed

- Day 5: foreground-aware remote pad/events published after owner approval, issue #5; host analyzer/tests and all three APK builds pass. Final CI/commit links are on the live card; physical acceptance stays Blocked.
- Day 6: installed-app phone grid and launch requests.
- Day 7: Android integration and honest alpha checkpoint.
- Day 8: iOS parity and local-network permissions.
- Day 9: revisioned row editor.
- Day 10: safe hide/unhide editor.
- Day 11: bounded tile/backdrop uploader.
- Day 12: optional user-owned TMDB setup and refresh.
- Day 13: status, polish and expanded mobile CI.
- Day 14: final acceptance, documentation, demos and gated release.

## Blocked / explicit limitations

- **Physical TV acceptance:** owner is away from the stick. API 22 TLS/NSD, enabled/disabled memory and measured latency remain pending.
- **iOS physical acceptance:** no physical iPhone/signing setup verified.
- **HOME takeover:** earlier accessibility attempt failed; not part of the new v1 architecture.
- **Other-app input and TV volume/power:** unsupported by design; do not list as promised v1 work.

## Daily board maintenance

At each approved day's start, add/link its one GitHub issue and move the card Ready → In progress. After each tested commit, append its link and actual evidence to the card. Keep unresolved hardware gates in Blocked. At completion link the final progress commit and CI result, move the day card to Done only for accepted scope, and make the next day Ready. Record changes in docs/PROGRESS.md in the existing end-of-day commit, not extra contribution-only commits. Maintain the 3–5 meaningful daily commit ceiling. No autonomous schedule is implied; updates happen during our approved work sessions.

Day 2 issue #2 is open; the reviewed HTTPS implementation was approved for publication on 2026-11-16. Physical-device acceptance remains pending. Do not mark it Done based solely on host tests.
