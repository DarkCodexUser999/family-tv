# Day 2 implementation review — 2026-11-16

**The owner approved publishing the four reviewed increments on 2026-11-16.** Source/build checks pass; nothing was installed on the TV and device acceptance remains pending. Issue #2 stays open for those gates. Future pushes still require the owner's applicable approval.

## Result

- Menu/Options opens Family TV settings with Remote access OFF by default, a toggle and connection details. The existing top-right shortcut still opens Fire OS Settings.
- Only an explicit enabled preference starts a non-exported, same-process LanService. No boot receiver, wake lock, foreground-service notification, network polling or separate process. Disabled startup creates no LAN-service worker/listener. When enabled, a bounded event worker handles Wi-Fi changes and an HTTPS acceptor serves private IPv4 Wi-Fi only; private peers only.
- Platform AndroidKeyStore RSA identity and TLS 1.2; certificate SHA-256 displayed on TV. Fingerprint trust still requires physical verification. Failure stays unavailable; no plaintext fallback.
- `/v1/info` returns the Day 1 schema and `pairingOpen:false`. Other endpoints are unavailable/unauthorized. This is not paired phone control yet.
- Bounded two-client runner, idle read timeout, total header deadline, duplicate/malformed header rejection, no bodies/temp files/compression/reverse DNS, one response per connection. Closing the listener closes active clients; no unbounded client queue.
- Stopping/updating the network is event-driven. Lifecycle status publication is guarded against old service instances. OS background restrictions beyond the target API 22 device are not solved by this prototype.
- Existing resolved launch intents, per-app flags, row/hide defaults, login data, loading state, BACK handling and restore script are preserved.

## Migration consequence

Legacy TCP RemoteServer no longer starts automatically. Existing native phone pairings are retained, but that legacy companion cannot control this candidate, even with the new toggle enabled. Pairing and navigation arrive in later days. Do not install this as an upgrade expecting an already working new phone remote. Current HOME accessibility code is unused; this server adds no accessibility dependency.

## Local evidence

- `sh tests/check-lan.sh`: 36 checks, including real loopback TLS with an explicitly trusted build-only certificate, response JSON, route/method/Origin/Host rejection, oversized headers, third-client rejection, stalled-client shutdown and closed-listener refusal.
- `sh tests/check-all.sh`: existing row/selection/navigation/input/protocol-proof checks plus the transport suite.
- `tests/check-protocol.py`: existing 27 fixtures (8 positive, 19 negative).
- Actual TLS response from `launcher/build/lan-check/info.json` validated against the Day 1 Info schema.
- Exact staged snapshots of each code increment passed the Java/host/protocol checks and both direct signed APK builds before publication. Documentation-only validation repeats the regression gates.
- Vendored core JAR is 51,211 bytes. Local launcher APK sample: 70,329 bytes; this is file size, not runtime PSS or a latency result.

The host TLS fixture uses a locally generated PKCS12 test identity under ignored build outputs, not the AndroidKeyStore adapter. It does not validate Android service preferences/receivers, physical TLS, or the visual dialog. The loopback opt-in constructor is package-private and used only by host tests; the production constructor rejects loopback/wildcard/public/IPv6 addresses.

## Physical acceptance still blocked

On the API 22 stick: verify OFF means no listener or legacy TCP socket, toggle ON and inspect fingerprint/HTTPS response from a same-LAN client, reject an untrusted certificate, change Wi-Fi and verify old endpoint closure, disable during an active connection, relaunch and verify preference behavior. Record disabled/enabled PSS and focus/launch timings under comparable conditions. Targets remain <=5 MiB incremental idle PSS (review if >8 MiB) and no repeatable focus/launch regression. No physical measurements or emulator results exist for this work.

## Approved commit sequence

1. `chore: pin the tiny transport dependency and portable build integration`
2. `feat: add bounded TLS-only info transport and host regression checks`
3. `feat: add default-off launcher remote access lifecycle and settings`
4. `docs: record day two evidence migration and pending device gates`

Each code increment was built/tested before its approved push. Keep issue #2 open while essential device acceptance is pending; update the project board with the actual stage instead of marking the device gate Done.
