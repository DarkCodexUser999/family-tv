# Week 1 Android alpha — Days 6–7

Version: `0.1.0-alpha.1+2`. Date: 2026-11-24. Issues: [Day 6](https://github.com/DarkCodexUser999/family-tv/issues/11), [Day 7](https://github.com/DarkCodexUser999/family-tv/issues/12).

This is a development candidate. No phone or Fire TV was connected during this session. Host tests and a built APK do not prove Android/Fire OS runtime behavior, secure storage, NSD, memory or latency.

## Build and install later, when hardware is available

From the repository root on ExSSD, with the SSD-backed FamilyTVTools image mounted:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
sh tests/check-all.sh
sh scripts/check-mobile.sh
FAMILYTV_REQUIRE_INTEROP=1 sh scripts/check-remote-interop.sh
sh build-launcher.sh
sh scripts/build-mobile.sh
```

Outputs: TV `launcher/build/local/family-tv-debug.apk`; phone `mobile/build/app/outputs/flutter-apk/app-debug.apk`. The phone requires Android 8/API 26+. Flutter, Pub, Gradle and build workspaces use the SSD; macOS preferences/temporary files can still use internal storage. The iOS project is source-only and was not built/tested here.

Install with existing signing keys and `adb install -r`; select the correct TV/phone serial when both are connected. Do not uninstall or clear data to work around a signature mismatch. APKs are not committed and the Android debug build is not a store release.

## Phone and TV walkthrough

1. Open Family TV on the TV. In its own options, turn **Remote access ON**. The Activity rebuilds once to prepare the optional phone catalog; it stays OFF by default.
2. Open connection details and Pair a phone. On the phone, discover the TV or enter the displayed private IP and port. Verify the complete certificate fingerprint before entering the six-digit PIN. Keep both devices on home Wi-Fi.
3. Open remote. The pad controls foreground Family TV only. Select the Apps icon to browse a searchable grid of installed non-hidden apps.
4. Tap Open once. Acknowledgement means the existing TV tile callback was dispatched, not that playback or sign-in succeeded. The tile's original launch route/task flags/loading state are reused.
5. Use the **physical remote inside the opened app**. The phone cannot inject Back, volume, power or other keys into that app. Its own BACK behavior determines the return path; record the actual result.
6. Backgrounding the phone closes app requests; Refresh is required on return. Launches never replay automatically. If a request times out, check the TV before retrying.
7. A package install/update/removal invalidates the TV snapshot and recreates Family TV with saved focus. Refresh the phone grid. Hidden apps, including Sony LIV, remain excluded.
8. If the IP/port changes, return to pairing, forget the saved connection and deliberately pair the new endpoint. A revoked token also requires pairing again. Never bypass a changed fingerprint.
9. Turn Remote access OFF to close listeners/discovery and release the optional snapshot. The restore script is unchanged; see SETUP.md.

## Evidence and remaining acceptance

| Gate | Result |
|---|---|
| Flutter analyzer and unit/widget suite | Pass, 31 tests; includes full injected pairing → pad → apps → return walkthrough, fake persistent-store recreation and endpoint recovery |
| Real host Dart ↔ Java TLS/WSS | Pass; cached page/icon, launch ACK, duplicate suppression, hidden/stale/revoked rejection and pad lifecycle |
| Native host regression suite | Pass; includes 70 TLS guards, 30 pairing checks, 29 pad checks, 45 WSS checks and 10 explicit-launch queue cases |
| Protocol | Pass; 10 positive and 22 negative fixtures, OpenAPI/schema/auth guards |
| APKs | TV v1/v2/v3 verified; Flutter Android alpha built once and v2 signature verified, API 26→36, ARM32/ARM64/x86_64 |
| GitHub CI | Inspect the run linked on the project board; local success is not a claimed CI result |
| Physical Android/Fire OS | Pending: installation, discovery/PIN UX, secure storage/restart, Wi-Fi loss/revoke, actual foreground app and BACK, same-build PSS and focus/launch timings |
| iOS | Pending Xcode build, permissions, discovery and device testing |

`mobile/test/android_flow_test.dart` is a **host widget walkthrough**, not Android instrumentation. The fake store does not test the device keystore. `remote_interop_test.dart` uses actual TLS/WSS and production clients against a host-only fake launcher UI. Neither substitutes for hardware acceptance. Issues #11/#12 stay open until those physical gates can be recorded.

No stable release or performance claim is made. Week 2 begins with Day 8 iOS parity after a fresh briefing/go; no Day 8 feature is included here.
