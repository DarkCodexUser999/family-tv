# v1 security and acceptance rules

Contract-only on Day 1. No endpoint, authentication filter or token issuer exists yet. A passing fixture does not prove transport enforcement.

- Server OFF by default; enabling it must not leave the legacy TCP listener active as a bypass.
- Same process as launcher, private Wi-Fi interface only, no root/accessibility/system permissions or global key injection.
- HTTPS/WSS with explicit TV fingerprint pinning; never fall back to cleartext or globally accept untrusted certificates.
- Pair only during a TV-opened 120-second window. Six-digit PIN, max five guesses per window plus address throttling; consume the session once. The all-zero PIN in fixtures is synthetic, not a usable credential.
- Tokens: 256 random bits, hashed on TV and stored securely on phone; never URLs, screenshots, committed files or logs. Revoke promptly and close authenticated sessions.
- Authentication required by default. Only minimal info and window-gated pairing are public; no app catalog or provider secret on public routes.
- Foreground-only semantic controls. HOME/BACK refer to Family TV, not other apps; volume, power, shell commands, arbitrary URLs and intent components are not accepted actions.
- Headers <=8 KiB, JSON <=16 KiB, WS <=4 KiB, uploads <=2 MiB. Reject before expensive parsing, bound connections/queues, one image worker; exact defaults in ARCHITECTURE.md.
- Expire inputs after 500 ms; deduplicate command IDs per authenticated phone. Reject mismatched envelope/payload IDs. Reconnect must never replay queued input.
- Mutations require If-Match and matching revisions; a stale edit returns 409. No duplicate packages within or across configured rows. IDs must be installed/allowed, never filesystem paths.
- The fixture validator checks schema and cross-field shape only. It cannot establish installed-package membership, PIN expiry, TLS security, token revocation, rate limiting, actual memory or latency. Test these in the real server before acceptance.
- TMDB credentials are write-only configuration. Uploads/provider/job endpoints are later milestones and not yet in the executable foundational contract; do not treat future documentation as shipped functionality.
- Preserve existing launch routes, task flags, login data, hide defaults (including Sony LIV) and restore behavior.

## Acceptance before any server release

Exercise disabled state, wrong PIN, PIN expiry/reuse, revoked tokens, malformed frames, oversized bodies, traversal, slow clients, network change and foreground handoff. Run on API 22 and measure same-build disabled vs enabled PSS and input/launch timings. Targets are ceilings, not current measurements. Keep v1 prerelease if physical-device acceptance is unavailable.
