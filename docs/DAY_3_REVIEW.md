# Day 3 implementation review — 2026-11-17

Status: owner approved publication on 2026-11-17; code published in four tested increments. Issue #3 remains open; physical acceptance is unavailable. Day 2 issue #2 also remains open. Built on fb91579.

## What is implemented

Options → Remote access ON → Remote connection details shows the ephemeral HTTPS address, SHA-256 certificate fingerprint and current discovery status. Pair a phone opens a 120-second six-digit PIN. The developer client must compare/pin the TV fingerprint before submitting any credential. GET /v1/info includes pairingSessionId only while a TV-opened session is active. POST /v1/pair accepts exactly sessionId, pin and deviceName strings. Content-Type must be application/json; Content-Length must be 1–1024 UTF-8 bytes; no chunked body, query, browser Origin, duplicate header/key, extra JSON field or trailing JSON. Larger requests are closed before parsing; body reads share the existing four-second monotonic header deadline and idle socket timeout.

The pairing session is consumed once, including when durable storage fails. Five guesses close the window. A separate per-address five-attempt and global twenty-attempt/120-second limiter survives TV window reopening. The token is 32 random bytes represented as 64 hex characters; TV stores only SHA-256 digests, with at most eight phone IDs. Persistence uses private SharedPreferences commit on a LAN worker before issuing a token. Revoke all phone access requires TV confirmation and durable removal; legacy records are kept separate. Individual phone labels/revocation are not implemented; revoke all and re-pair retained phones.

All non-public requests require a bearer token. Authenticated but unimplemented routes return 404; unauthenticated routes/upgrades return 401. WSS is NOT implemented yet and no upgraded sessions exist to disconnect. This is the shared upgrade authentication boundary, not a working WebSocket remote. /v1/status, controls, app catalog and Flutter UI are later work.

NSD advertises _familytv._tcp. with the actual ephemeral port and only api=1 metadata. Registration exists only with the enabled transport; late registration after stop triggers unregister. Failure leaves the manual HTTPS address available. There is no polling, wake lock, extra process, new permission, accessibility integration or new runtime dependency. Platform unregister failures are surfaced; actual platform cleanup needs physical verification. Implementation follows Android's NSD lifecycle: https://developer.android.com/develop/connectivity/wifi/use-nsd.

## Evidence and limits

- 47 real loopback TLS/transport checks, including successful pairing, replay, invalid token, revocation, oversized body and upgrade rejection.
- 30 deterministic pairing/hash/persistence/expiry/throttle/strict JSON checks.
- 13 discovery lifecycle assertions against host-only Android fakes, excluded from APK source sets. This does not prove Fire OS NSD/multicast.
- Existing Java regressions, configuration/manifests/shell checks and 27 protocol fixtures pass.
- Both Android APK builds and signature checks pass. Build min API 22 for launcher; this is not execution on API 22. Flutter checks N/A with no mobile package.
- No TV/emulator installation or memory/latency/login/Home/Back claims. Source diff preserves launch routes, hidden apps, restore script and loading state.
- Each exact staged snapshot passed applicable local checks and both builds before commit/push. The final CI result/URL is recorded on the project card after publication; no success is assumed in advance. Generated test certificates and APKs remain ignored on SSD.

## Approved publication

1. feat: add expiring pairing windows and hashed phone credentials — core and deterministic unit tests.
2. feat: gate pairing requests and protected routes over bounded TLS — transport, schema and wire tests.
3. feat: add opt-in discovery and TV pairing controls — lifecycle, UI and host NSD tests.
4. docs: record pairing setup and day three verification limits — docs/evidence/board links.

Each staged snapshot passes applicable tests and both builds before commit/push. Security core and lifecycle test groups may exceed ~100 handwritten lines because splitting a credential issuer from its persistence/expiry tests or a discovery adapter from its lifecycle tests would weaken review. Four cohesive increments, no quota padding. Owner approval was received. Code commits: f13f5b6 (pairing core), 0295e50 (bounded TLS/auth), 156fbb2 (discovery/TV settings).

## Developer/physical demo once hardware is available

1. Enable access and confirm address/fingerprint; verify no legacy TCP listener and compare OFF/ON PSS and focus/launch timings.
2. On a client that explicitly pins that fingerprint, inspect /v1/info. Open Pair a phone on TV and submit its PIN/session using the documented JSON; never put credentials in URLs or logs.
3. Verify an authenticated unavailable route is 404, then revoke all on TV and confirm the same bearer is 401. Wrong PIN, expiry and replay must fail.
4. Discover the same TV from two phones, test denied multicast/manual endpoint fallback, disable access and verify advertisement/listener removal, including Wi-Fi loss/reconnect.
5. Confirm existing streaming launches and BACK behavior on the stick. New phone pad is not available until later milestones.
