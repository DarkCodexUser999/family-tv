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

Core is a small Java transport and supports supplying a TLS socket factory. It is an older dependency, not independently security-audited here. Review focused on its request buffering, worker creation, body/temp-file behavior, reverse DNS and shutdown before limiting the exposed surface. NanoWSD is NOT included or enabled yet; revisit its framing/auth/limits separately on Day 3.

## Mandatory adaptations

Stock defaults are not accepted: a bounded runner permits two clients and no queue; single-request headers are capped at 8 KiB with a 1.5-second socket read timeout and four-second overall header deadline. Duplicate/malformed header names are rejected. Each connection closes after one response. No request-body parsing, temp files, reverse DNS or compression are used. Origin/Host checks apply. Only the minimal `/v1/info` identity exists; every other route returns unauthorized until pairing/auth is implemented. No command handler or file-serving route is registered.

The direct build verifies the artifact SHA-256 and includes it in D8; Gradle uses the same local JAR. Direct compilation is recursive and uses an isolated temporary class directory, avoiding stale classes; generated class archives exclude macOS AppleDouble metadata. Downloaded dependency and license are intentional source dependencies, not APK outputs. Both builds remain local pending push approval.

## Remaining gates

Actual Fire OS TLS/AndroidKeyStore interoperability, service lifecycle behavior, disabled/enabled PSS and navigation/launch impact have not been measured. Host TLS tests are not a device audit. Do not release the candidate as generally supported until these gates pass. No new root, accessibility or system permission is used; the two new permissions are normal Wi-Fi/network state access.
