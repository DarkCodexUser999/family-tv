# Day 10 — reversible hidden apps

Development version `0.2.0-dev.2+4`, issue #15. Apps remain installed; no streaming account, package setting or app data is modified.

## Phone walkthrough

Open Remote → Apps → Hidden apps. Search by name, switch ON to hide or OFF to show, then Save. The confirmation names each change, including **Show Sony LIV** if explicitly selected. Cancel leaves the draft unsaved. Sony LIV stays excluded through ordinary edits unless explicitly unhidden. Defaults for packages without a current launcher activity are listed with disabled switches; showing an unavailable package cannot create a working tile.

Saved changes remove tiles from all Family TV rows/favorites. Configured row membership is retained, so unhiding can restore its position. Hiding the focused app reuses the existing package-focus restoration with the first valid favorite/settings fallback. Safe rebuilds defer while a launch/dialog is active or until returning from another app. This lifecycle behavior remains unverified on hardware.

A second phone's newer save causes a revision conflict. Your draft remains visible; Reload asks before discarding it. Backgrounding cancels requests and requires explicit reload before another save. Drafts live in memory and do not survive process termination. There is no automatic save or launch replay.

## Storage, API and bounds

- Bundled `hidden-apps.txt` remains unchanged. A complete private `remote-hidden.json` override, at most 8 KiB/256 unique package identifiers, takes priority after saving. Corrupt/unreadable overrides fall back to bundled exclusions. Use the phone to reverse visibility; changing the asset alone does not replace a valid saved override.
- Authenticated `GET/PUT /v1/hidden` uses a revision plus quoted If-Match. Writes reuse the private AtomicFile adapter on the HTTP worker and publish only after success. Reject invalid/duplicate/unknown packages, stale revisions and revoked access.
- Authenticated `GET /v1/hidden/apps` pages the cached installed launchable catalog, including hidden entries (25/page, at most 64). Normal app lists/icons and explicit launch routes exclude hidden packages. Existing image records are shared between catalogs, not duplicated.
- Hiding immediately replaces the launchable snapshot. Old accepted-request IDs cannot bypass the new exclusion; tile click and deferred dispatch check the cached hidden set as well. No PackageManager, decoding or disk access is added at click time.
- The private hidden override remains active with remote access OFF. Restore/task flags are unchanged. `--home-only` retains app preferences; uninstalling Family TV removes its private preferences. No new dependency, process, accessibility or system permission was added.

## Evidence and acceptance

Passed locally: native suite including 83 TLS guards and 15 hidden-store fake-disk checks; Sony default/unhide, conflict, failed write, corruption fallback and reload; actual pinned Dart/Java TLS hide/unhide and rejection of a replayed launch while hidden; phone widgets/controller checks for named confirmation/cancel, drafts, background saves, search/large text and unavailable default policy. Signed launcher and Android APK evidence is recorded in PROGRESS.md. CI result is linked on the project board.

No physical phone/TV/emulator session ran. Actual tile disappearance/reappearance, focused-tile fallback, dialog/queued-key races, app account continuity, Android crash recovery, PSS/latency and native iOS remain unverified. Host fake-disk/widget tests do not prove those outcomes. Issue #15 stays open for hardware acceptance. Day 11 has not started.
