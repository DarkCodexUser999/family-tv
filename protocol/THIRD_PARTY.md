# Transport dependency review — Day 2 local candidate

## NanoHTTPD core 2.3.1

- Maven coordinate: `org.nanohttpd:nanohttpd:2.3.1`.
- Vendored artifact: `launcher/libs/nanohttpd-2.3.1.jar` (51,211 bytes).
- Source/tag: https://github.com/NanoHttpd/nanohttpd/tree/nanohttpd-project-2.3.1
- Artifact: https://repo.maven.apache.org/maven2/org/nanohttpd/nanohttpd/2.3.1/nanohttpd-2.3.1.jar
- Published SHA-1 checked: `a8d54d1ca554a77f377eff6bf9e16ca8383c8f6c`.
- Pinned SHA-256: `de864c47818157141a24c9acb36df0c47d7bf15b7ff48c90610f3eb4e5df0e58`.
- License: BSD-3-Clause; complete notice retained in `launcher/libs/NANOHTTPD-LICENSE.md`. This does not select a license for the rest of this repository.
- No runtime transitive dependency in the published POM; Apache HTTP clients there are test-scoped, not included.

Core is a small Java transport and supports supplying a TLS socket factory. It is an older dependency, not independently security-audited here. Review focused on its request buffering, worker creation, body/temp-file behavior, reverse DNS and shutdown before limiting the exposed surface. Day 5 adds the separately reviewed NanoWSD source described below; the core JAR is unchanged.

## Mandatory adaptations

Stock defaults are not accepted: a bounded runner permits two clients and no queue; single-request headers are capped at 8 KiB with a 1.5-second socket read timeout and four-second overall header deadline. Duplicate/malformed header names are rejected. Each connection closes after one response. No request-body parsing, temp files, reverse DNS or compression are used. Origin/Host checks apply. Only the minimal `/v1/info` identity exists; every other route returns unauthorized until pairing/auth is implemented. No command handler or file-serving route is registered.

The direct build verifies the artifact SHA-256 and includes it in D8; Gradle uses the same local JAR. Direct compilation is recursive and uses an isolated temporary class directory, avoiding stale classes; generated class archives exclude macOS AppleDouble metadata. Downloaded dependency and license are intentional source dependencies, not APK outputs. Source is published after owner approval; private signing keys and generated APKs remain excluded from Git. The complete BSD notice is also packaged as a launcher asset.

## Remaining gates

Actual Fire OS TLS/AndroidKeyStore interoperability, service lifecycle behavior, disabled/enabled PSS and navigation/launch impact have not been measured. Host TLS tests are not a device audit. Do not release the candidate as generally supported until these gates pass. No new root, accessibility or system permission is used; the two new permissions are normal Wi-Fi/network state access.

## NanoWSD 2.3.1 — Day 5 local candidate

- Maven source coordinate: org.nanohttpd:nanohttpd-websocket:2.3.1:sources.
- Source: https://repo.maven.apache.org/maven2/org/nanohttpd/nanohttpd-websocket/2.3.1/nanohttpd-websocket-2.3.1-sources.jar
- Published source SHA-1 verified: 2da79bf296879e85cb6700679809e4d6716e0f04.
- Downloaded source archive SHA-256: 0852067223ffa2c6cbf50536964296651f454c43f9bde4536615c7f8d65baa2b.
- Vendored file: launcher/src/main/java/fi/iki/elonen/NanoWSD.java. No separate runtime WebSocket JAR or transitive dependency.
- BSD-3-Clause copyright/conditions/disclaimer retained in source and packaged as launcher/src/main/assets/licenses/NANOWSD-LICENSE.txt. This notice does not license the rest of Family TV.

Local changes: protected input-stream constructor and frame-completion hook for one-shot deadlines; reject unmasked/over-4096-byte payloads before allocation; reject fragmented/continuation/binary frames. Existing upstream handshake/framing/UTF-8 validation remains in use. Authentication, strict Host/Origin/handshake checks, two-client cap, bounded queues, expiry/dedup, frame rate and read/write deadlines are application wrappers, not upstream guarantees.

Review used the upstream source and actual TLS/frame tests, including the Dart native client. There is no independent security audit or Fire OS compatibility/memory result. Dependencies are old and deliberately scoped; hostile-LAN resource bounds and API22 validation remain release gates. Build includes the recursive source and bundled license using the existing direct/Gradle flows.
