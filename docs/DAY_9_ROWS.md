# Day 9 — edit TV rows from the phone

Development version: `0.2.0-dev.1+3`. Issue #14. This adds to the frozen Week 1 alpha; no stable release or physical-device acceptance is claimed.

## Use it

1. Enable remote access in Family TV, pair the phone, then open Remote → Apps → Edit TV rows.
2. Move apps earlier/later, move them into another row, reorder entire rows, or pick an unassigned app. Use up to four rows and six configured apps per row.
3. Unassigning an app or removing a row does not hide/uninstall anything: leftover installed apps still appear under the launcher's existing last-row policy.
4. Save explicitly. The TV applies the change when it can safely rebuild its home, or when returning from another app. Existing package-based focus restoration is reused; actual focus behavior still needs the stick.
5. If another phone saved first, your draft stays visible and Save is blocked. Reload TV rows asks before discarding the draft. There is no silent overwrite or automatic save retry.
6. Backgrounding cancels phone requests. After an uncertain save, check/reload TV rows before trying again. Drafts survive that screen's backgrounding/conflict, but are in memory only and do not survive phone process termination.

## TV configuration and reversibility

Packaged `assets/rows.json` remains unchanged. The app stores validated overrides privately in `remote-rows.json` using Android `AtomicFile`; the file is at most 8 KiB. Default membership is filtered to installed/non-hidden targets at initialization. Existing leftover sorting, per-app launch routes, hidden defaults (including Sony LIV), sign-ins, app data and restore commands are preserved.

Remote access may be OFF while saved rows remain active. To change them again, turn access ON and use the editor. To undo an arrangement, move the apps back and Save. `restore.sh --home-only` retains app preferences; normal Family TV uninstall removes its private override along with the app. Do not clear data or uninstall merely to work around a save error.

## API and resource bounds

- Authenticated `GET /v1/rows` returns the cached immutable `{apiVersion,revision,rows}` document with a revision ETag. Reads do not access disk or PackageManager.
- `PUT /v1/rows` accepts at most 8192 bytes of strict UTF-8 JSON. Body revision must equal quoted `If-Match`. Reject missing/extra fields, duplicate IDs, >4 rows, >6 apps/row and absent/hidden catalog targets.
- A serialized writer validates authorization and revision before writing off the UI thread; 409 rejects stale edits. Successful state publication happens after the atomic write. Responses capture that save's exact revision before another writer can advance it.
- Explicit save events request a safe Activity rebuild; background saves apply on resume. No polling, new process, dependency or work in the focus/launch hot path is added. The store retains one small immutable document; actual PSS/latency remain unmeasured.
- The phone reuses pinned TLS, bearer auth, bounded responses and no redirects. Save failures keep the draft and require deliberate recovery.

## Evidence and pending checks

Passed: 44 phone unit/widget tests/analyzer, including grid → rows → move → Save → return; 22 row validation/fake-disk tests covering interrupted writes/reload and concurrent phones; 77 real TLS guards including row GET/PUT/conflict/hidden rejection; the production Dart row client against the actual Java TLS/WSS stack. Native regressions and signed launcher build pass. Android candidate build evidence is recorded in PROGRESS.md.

Fake-disk tests do not prove Android AtomicFile power-loss behavior. Physical TV application, focus preservation, background-return ordering, package-change races, memory and latency remain pending. iOS builds and actual Local Network/Keychain behavior remain blocked as documented in DAY_8_IOS.md. Issues #13/#14 stay open for those gates. Day 10 has not started.
