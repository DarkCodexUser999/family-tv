# Family TV Remote

Day 5 source approved and published; physical validation remains pending. One Flutter codebase for Android 8+/API 26 and iOS 15+ source. Pairing and a foreground-only Family TV remote pad use pinned HTTPS/WSS; existing TV launch logic is preserved.

## Run from the monorepo root

Use Flutter 3.50.0 / Dart 3.14.0 (SDK revision ab2912035e160d9cad6306df307576da6128ebdb). Native JDK 17 and the Android SDK are required for Android builds. Commit pubspec.lock; do not use an unreviewed dependency upgrade.

```sh
sh scripts/flutter.sh pub get
sh scripts/check-mobile.sh
sh scripts/build-mobile.sh
```

The wrapper keeps Pub, Gradle, Android user state and NDK on ExSSD where configured. macOS settings/temporary files may still use internal storage. On this Mac mount tools/flutter-tools.sparseimage first (FamilyTVTools). The Android build uses an APFS temporary source snapshot without AppleDouble metadata, then copies app-debug.apk to mobile/build/app/outputs/flutter-apk/. It removes only its own generated workspace. The repository stays on ExSSD; never reformat the SSD. The local SDK view links existing SDK components to the NDK on APFS. See docs/DAY_4_REVIEW.md for setup/limitations.

Do not run bare flutter test against an ExFAT directory: ._test.dart metadata is not source. check-mobile.sh passes the real test paths explicitly. The TLS suite creates an ephemeral, test-only certificate under ignored build/. Preview tests use the SDK font and render implemented widgets, not an emulator or a physical phone.

## Pairing flow

1. Put the phone and stick on the same home Wi-Fi. Enable Remote access in Family TV settings.
2. Find my TV, or manually enter the TV's private IPv4 address and ephemeral port. Discovery is a hint, not authentication.
3. Copy the full SHA-256 fingerprint from TV connection details into the phone. Open Pair a phone on the TV, then confirm the connection in the phone app.
4. Enter the six-digit TV PIN and a phone name. No automatic POST retry; repeated presses are blocked during the request.
5. After successful pairing, credentials are encrypted/Keychain-stored on the phone. A restored saved pairing is labelled unverified; no false reachability/token-validity claim.

The fingerprint is checked before any PIN is sent. The client rejects public/loopback/hostname targets, HTTP, redirects, proxy routing and oversized responses. Tokens/PINs never enter URLs or logs. Forget saved connection removes only this phone's local record; revoke access using Family TV settings on TV.

## Local build result

Android debug APK built and its signature verified: package app.familytv.family_tv_remote, min API 26, target API 36, arm64-v8a/armeabi-v7a/x86_64. The debug universal APK is about 159 MiB; release size is not measured. Both legacy APK builds and the new app checks pass locally; Day 5 source is published on main after owner approval. No physical-phone verification is implied.

## Physical acceptance still pending

No TV/phone/emulator install ran. Android APK compilation alone does not establish actual NSD, Android Keystore or Fire OS behavior. Only Apple command-line tools are installed; no iOS build/signing or Keychain/LAN consent test is claimed. No paid Apple account or cloud service is required for this source work.

Permissions: Android INTERNET and CHANGE_WIFI_MULTICAST_STATE; iOS local-network description and _familytv._tcp Bonjour declaration. Android backup/cleartext disabled, iOS Keychain non-syncing and device-only access configured. No location, camera, accessibility, contacts, root or ADB used by the app.

See docs/MOBILE_DEPENDENCIES.md for reviewed versions/licenses and docs/DAY_4_REVIEW.md for actual evidence.

The original 8 GiB tools image filled during the first full build and was safely grown offline to 16 GiB on 2026-11-18, preserving its files. This changes only the sparse tools image on ExSSD, not the physical drive format.

## Remote pad — Day 5 candidate

After pairing (or loading saved credentials), choose Open remote. The initial authenticated WSS state must arrive before the eight buttons enable. Home/Back/Options apply inside Family TV only. Starting another app disables controls; return with the physical remote. No TV power/volume or global streaming-app navigation.

One input at a time with visible feedback; no automatic action retry. A missing reply is uncertain, so reconnect never repeats it. Backgrounding closes the session/cancels retries; foreground recovery gets current state. Network reconnect is bounded to three consecutive failures at 1/2/4 s. The TV rejects stale or repeated commands independently.

```sh
sh scripts/check-mobile.sh
sh scripts/check-remote-interop.sh
```

The second script runs the real Java transport and production Dart WSS connector on a private host interface with a fake launcher UI and synthetic credentials. It needs JDK17; no private interface produces an explicit skip. No hardware memory/latency or actual NSD claim. The remote preview is a Flutter widget render, not a device screenshot. See docs/DAY_5_REVIEW.md and protocol/THIRD_PARTY.md.
