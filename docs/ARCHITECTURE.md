# Family TV Remote architecture

Status: Days 6–7 implemented on 2026-11-24: default-off HTTPS pairing/discovery, foreground-only WSS pad, cached app catalog/icons and revision-bound launches from the Flutter grid. Host integration and Android build evidence are in WEEK_1_ALPHA.md. Editing, uploading, phone-side provider configuration and richer status remain planned; unimplemented routes return 404. Legacy TCP and accessibility takeover are removed. Physical Fire OS/iOS TLS/NSD, memory and latency acceptance remains pending.

## Stack and repository

| Path | Responsibility |
|---|---|
| `/launcher` | Existing Java/native-Views API 22 launcher; new `lan/` adapter, bounded HTTP/WS server, pairing store, settings toggle and NSD registration |
| `/mobile` | Published Flutter pairing app source; later features planned: `lib/core/{api,discovery,storage}`, `lib/features/{pairing,remote,apps,rows,artwork,status}`, `test/`, `integration_test/`, generated Android/iOS hosts |
| `/protocol` | Planned `openapi.yaml`, `events.schema.json`, `fixtures/`, `SECURITY.md`; language-neutral v1 contract |
| `/docs` | Architecture, PLAN.md, PROGRESS.md, setup, acceptance evidence and release notes |
| `/remote`, `/shared` | Legacy Android companion and challenge-response protocol; preserve until migration acceptance; never run both listeners by default |

Use Flutter stable pinned on Day 1, Dart null safety and one shared UI/API client. Phone targets: Android 8+ (API 26) and iOS 15+; Flutter never runs on the API 22 stick. Start with SDK `ChangeNotifier`/`ValueNotifier`, HTTP and WS clients; add only reviewed packages for Bonjour/NSD, secure credential storage and image selection. Pin versions and commit `pubspec.lock`; track licenses. No Firebase, cloud relay or mobile ADB.

Keep the launcher Java/API 22, no Compose/native libraries. Evaluate pinned NanoHTTPD + NanoWSD (BSD license review, parser/security and API 22 gates) as a small transport rather than hand-writing HTTP/WS framing. Supply a bounded request runner: library defaults are not an acceptable resource policy. Integrate the pinned source/JAR and notices in both the direct shell build and Gradle; maintain current CI. Reject the dependency if it misses the memory gate. TLS uses the platform provider, not an added native library.

## Launcher ownership and lifecycle

A **Remote access** setting is OFF on fresh install. Enabling starts an in-process `LanService` with no `android:process`, root, accessibility, secure-settings grant or input injection. Pairing opens only by a separate TV action. Disabling closes active clients, unregisters NSD and stops the service; **Forget phones** revokes tokens independently. The service can remain reachable when another app is foreground, but reports `canNavigate:false`; background-start restrictions on other OS versions must be tested, not assumed solved by API 22 behavior. No permanent foreground service or wake lock.

`LauncherBridge` publishes immutable cached catalog/status snapshots, dispatches bounded semantic UI commands and receives explicit lifecycle/focus updates. Network code never queries PackageManager, reads images or blocks on disk from the focus/click path. Edits validate on a worker, atomically persist app-private overrides, then post one small UI update. Packaged asset files stay baseline defaults, with runtime overrides layered on top. Never edit per-app launch flags through the public API or change stock Amazon packages.

Use existing resolved app components and cached launch intents. Permit launching only installed, non-hidden catalog entries. Existing third-party accounts/data stay untouched. Phone controls act on Family TV only: no global Back/Home, volume, power or key injection into streaming apps. App-list, settings and configuration endpoints can still operate while navigation is unavailable. A user can reopen Family TV using its physical remote; v1 does not promise background activity launch from every OS.

## Day 3 implementation boundary

Current pairing accepts at most 1024 UTF-8 bytes and stores only hashed tokens and random device IDs (device labels are validated but not persisted). TV revocation removes all phones; individual phone management and a pairing QR are later work; bounded WSS sessions are present in the Day 5 candidate. /v1/info adds pairingSessionId only while the explicit TV-opened window is active. Tests prove host TLS and fake-platform callback behavior, not Fire OS keystore, multicast cleanup, memory or latency. The following sections describe target behavior where features extend beyond that boundary.

## Discovery, pairing and transport

Advertise `_familytv._tcp.` via Android NsdManager only while enabled, with TXT `v=1` and a random installation ID (no PIN/token/account/MAC). Phones discover on the local network; manual IP/port is the fallback for blocked multicast. Prefer a port assigned at bind time, advertised through NSD; manual setup displays the current port. Bind only the active private Wi-Fi interface, not wildcard/cellular/VPN; close/rebind and re-advertise on network changes. Reject public/loopback manual targets outside explicit developer tests. No port forwarding or WAN listener.

Use HTTP semantics over **HTTPS** and WebSockets over **WSS** so PINs, tokens and the TMDB key are not sent in plaintext. Generate a per-install certificate/key with Android's public keystore APIs; verify self-signed server TLS compatibility on the actual Fire OS build in Day 2. Display the certificate fingerprint with the pairing QR/address on the TV; the phone pins this fingerprint before sending a PIN. Manual setup requires explicit fingerprint confirmation. Discovery alone is not identity. Do not silently fall back to HTTP, accept all certificates globally or disable verification. If API 22 TLS cannot meet the gate, stop and revise the design openly.

Pairing: TV user opens a 120-second window → random six-digit PIN and new session ID → phone verifies displayed certificate fingerprint → `POST /v1/pair` with PIN, session ID and device label → constant-time comparison, rate limit and atomic one-use consumption → random 256-bit token returned once. Limit five attempts per window globally plus per-address throttling; close on success or expiry. Store only token hashes, bounded labels and IDs on TV; phone stores token plus fingerprint in Keychain/Android secure storage. Tokens never enter URLs, NSD, logs or screenshots. Reopening pairing creates a new window; ordinary API calls cannot open it. Max eight paired phones; explicit TV approval to revoke/add beyond the limit.

Normal HTTP uses `Authorization: Bearer <token>`. Native WS upgrade uses the same header, with Origin checks and no query-string token. Reject browser origins by default, disable CORS, validate Host and require auth before allocating an upgraded connection. WSS accepts one client per phone and two total; revoke/disconnect immediately on disable or token deletion. Certificate reset means deliberate re-pairing. Secure storage and API 22 keystore behavior are device gates, not completed claims.

## v1 API contract

All JSON is UTF-8. Responses include `apiVersion:1`; errors use `{error:{code,message},requestId}` without secrets. `/v1/info` alone exposes a minimal discovery identity; `/v1/pair` is usable only during the TV pairing window. Everything else requires auth. Cache catalog/config snapshots and include `revision`; mutations require `If-Match`, returning 409 on stale edits. Return 400 invalid input, 401 bad token, 403 disallowed action, 404 missing resource, 409 conflict/unavailable UI, 413 size limit, 429 rate limit, 503 busy.

| Method/path | Request → result |
|---|---|
| `GET /v1/info` | Minimal installation ID, API version and pairing-open flag; no app list or secrets |
| `POST /v1/pair` | `{sessionId,pin,deviceName}` → `{deviceId,token}`; explicit TV window only |
| `GET /v1/status` | Cached enabled/foreground/canNavigate/catalogRevision/artwork job status; TMDB configured boolean only |
| `GET /v1/apps` | Paginated cached `{package,label,hidden,iconRevision}` list; bounded local thumbnail endpoint |
| `GET /v1/apps/{package}/icon` | Cached PNG ≤8192 bytes at 96×54; authenticated, no-store, no filesystem path |
| `POST /v1/actions` | `{id,action}` with UP/DOWN/LEFT/RIGHT/OK/BACK/HOME/OPTIONS; semantic bridge, foreground only |
| `POST /v1/apps/{package}/launch` | `{id,revision}` → `{apiVersion,id,accepted}`; existing tile callback, reject hidden/missing/stale apps |
| `GET/PUT /v1/rows` | Read or replace ≤4 rows, ≤6 unique package IDs each, `If-Match`; unknown IDs rejected; leftovers policy preserved |
| `GET/PUT /v1/hidden` | Read or replace hidden IDs, `If-Match`; includes Sony LIV baseline and never disables/uninstalls anything |
| `PUT/DELETE /v1/artwork/{package}/{tile|backdrop}` | One streamed JPEG/PNG upload or delete override → revision; strict installed-package allowlist |
| `PUT/DELETE /v1/settings/tmdb` | Set `{key}` or remove → `{configured}` only; never echo the secret |
| `POST /v1/backdrops/refresh` | `{scope:"focused"|"all"}` → 202/job ID; coalesce duplicate jobs, one worker, cancellable |
| `DELETE /v1/pairings/{deviceId}` | Revoke own device; deleting other phones is TV-only |
| `GET /v1/events` (WSS upgrade) | Authenticated actions/acks, focus, lifecycle, revisions and job progress |

WS envelope: `{v:1,type,id,payload}`; commands receive `ack` or `error` with the same ID. Input expires after 500 ms; bounded dedup cache retains the last 64 IDs per phone for at most ten seconds; cached duplicates never re-execute. The current dispatcher has ≤8 queued inputs globally (stricter than the per-client target); coalesce focus/status output and disconnect clients that do not read. No continuous focus polling or application heartbeat; rely on lifecycle/network events, and bounded reconnect backoff only while the phone is active. Reconnect receives a fresh state snapshot; old queued actions are never replayed. Actions are capped at 20/s/phone and 30/s globally; HTTP 10 requests/s/phone with small burst capacity.

## Artwork, TMDB and configuration

Upload one binary body (not base64), ≤2 MiB, decoded bounds ≤4096×4096 and ≤8 megapixels. Validate magic bytes/type, never filenames supplied by a client; sample down before decode, cap tile at current output size and backdrop at 640×240 RGB_565. Single upload worker, atomic file replacement, 32 MiB total override quota, no arbitrary URLs, EXIF stripping through re-encoding, and no decode on the main thread. Invalid files leave the previous artwork intact. Runtime edits never modify `restore.sh` or packaged hidden/task defaults.

Batch 3 implements optional TV-side TMDB lookup using the owner's external key file and platform HTTPS. A worker resolves India provider IDs, ranks recent movies and series, and publishes validated files into the private cache. The status API exposes only a configured boolean. Physical OS TLS, cache behavior and focus timing still require device verification.

## Resource and security gates

| Resource | Proposed acceptance ceiling; measure rather than assume |
|---|---|
| Disabled | No server sockets, advertisement, server threads or timers; cached focus/lifecycle writes only, no focus allocation or I/O |
| Enabled idle | Aim ≤5 MiB incremental PSS, hard review gate >8 MiB versus same-build disabled baseline |
| Threads/process | Same launcher process; one acceptor, ≤2 HTTP workers + ≤2 WS readers, one sleeping event writer and one one-shot deadline worker; no extra process. Future media worker remains planned |
| Payloads | HTTP JSON 16 KiB, headers 8 KiB, WS frame/message 4 KiB, streaming upload 2 MiB; bounded connection/read/idle timeouts |
| Queues/cache | ≤8 inputs globally, ≤8 replies/phone, coalesced state, 64 recent IDs/phone; four small backdrop bitmaps. Media jobs remain planned |
| UI/performance | No blocking I/O or parsing on focus/click; no repeatable regression beyond measurement noise (initial gate >5% median or >10 ms p95 requires investigation) |

Private LAN is not a trust boundary: pin TLS, compare token hashes safely, bound work before parsing, deny traversal/shell intents, test malformed frames and revocation, and minimize logs. Pairing tokens are authenticated transport credentials, not permission to change system settings. Preserve the existing restore command; extend only when needed to stop the new service/unregister NSD, keeping other apps and accessibility services intact. No new INTERNET/system capabilities are assumed beyond the existing normal network permissions and platform-specific local-network consent.

## Migration and validation

The launcher hosts the opt-in HTTPS/WSS transport. The native phone project keeps the old TCP client for reference. Its saved pairings are removed only by the explicit legacy-forget option or uninstall. The restore script recognizes the historical HOME service on older installations.

Use protocol fixtures, JVM/host tests, Flutter unit/widget tests and analyzer before pushes once `/mobile` exists. CI builds launcher/API 22 and Flutter Android, then a macOS iOS simulator build when capacity is available. Mock bridge/phone demos validate logic but cannot establish Fire OS latency, NSD/TLS behavior or memory. No physical TV/iPhone is currently available; track these as pending gates and withhold a stable v1.0 release if essential acceptance cannot run. iOS simulator testing does not prove physical LAN discovery, and App Store/TestFlight distribution requires signing/account decisions beyond a free local simulator build.

Sources: [Flutter platform support](https://docs.flutter.dev/reference/supported-platforms), [Android NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd), [Apple LAN privacy](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy), [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd). Pin and verify exact dependency versions before implementation; this document specifies targets, not completed benchmarks or a completed license audit.

## Day 5 wire details (implemented local candidate)

Native WSS uses the same pinned certificate and bearer header as HTTPS. Initial and coalesced `state` payloads contain foreground, canNavigate, focused package/null, even lifecycle epoch and serverTimeMs (monotonic). Action payloads add sentAtMs and epoch to matching envelope/payload IDs; the phone advances the received server time with its Stopwatch. Reject stale lifecycle/input before posting and again on dispatch. A recent duplicate returns its previous result without replay. State and acknowledgements are serialized off the focus path.

Use scripts/check-mobile.sh and scripts/check-remote-interop.sh for Flutter/analyzer and real Dart-to-Java host interoperability. The latter needs JDK17 and private IPv4 and is not a device/performance test. App launching through the pad's OK reuses existing handlers; the explicit phone app grid is Day 6. Minimal /v1/status has catalogRevision=0 and the current credential-configured boolean. HTTP /v1/actions is still reserved; current control transport is WSS only.

## Implemented app browsing bounds — Days 6–7

`AppsEndpoint` retains up to 64 non-hidden entries and ≤512 KiB of encoded PNGs, prepared after the home view is built, on the existing artwork background executor only when remote access is ON. Pages contain at most 25 entries; cursors bind offset to snapshot revision. Canonical query order is `cursor` then `limit`; unknown/duplicate/encoded query fields fail closed. No thumbnails are decoded by request handlers. This is a retained-data bound, not measured PSS.

`AppLaunch` permits one pending request, expires queued work after 500 ms and caches at most 64 accepted phone/request-ID pairs. Reused IDs are bound to package/revision. Authorization, foreground epoch, busy state and snapshot identity are checked before the UI dispatch; the existing tile callback supplies feedback and per-app cached launch flags. ACK means click dispatched, not first frame or login. Package broadcasts invalidate the snapshot and recreate the Activity with saved focus.

The phone uses pinned TLS for catalog, icons and launches, with 8-second request deadlines, 16 KiB JSON/8 KiB image caps and no redirects/proxy. The grid closes its requests when backgrounded; manual Refresh is required on return. Opening Apps suspends the pad socket until that screen is popped. New addresses require explicit forget/re-pair; tokens are never silently sent to another endpoint.
