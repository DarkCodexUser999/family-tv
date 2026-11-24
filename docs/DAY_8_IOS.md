# Day 8 — iOS source parity

Authorized with Day 9 on 2026-11-24. Issue #13. Week 1 Android alpha is tagged separately at `v0.1.0-alpha.1`; no iOS binary is attached to it.

## Delivered

- iPhone onboarding explains Local Network consent before user-started discovery, including manual-address requirements and explicit retry after returning from Settings.
- The pinned `nsd_ios` 3.0.1 native error mapper reports most failures as `internalError`, not `securityIssue`. Recovery now mentions iPhone Local Network settings even for that generic failure, without claiming every failure is a denied permission.
- The production credential-store channel is exercised with iOS options: device-only first-unlock accessibility, no synchronization, write/read-back verification, new-store reload, corrupt/locked-store recovery and deletion. The channel is mocked: actual Keychain behavior is not proven.
- Source guards check Bonjour service, local-network explanation, no broad ATS exceptions, both entitlement references and iOS 15 deployment settings. No runtime dependency or Apple-specific TV control path was added.
- Shared pinned HTTPS/WSS and lifecycle tests from Week 1 apply to the same iOS Dart source. iOS consent/reconnect is additionally exercised through a host widget branch; no permission polling or automatic input replay.

## Tooling limitation and future validation

This Mac selects `/Library/Developer/CommandLineTools`; `xcrun --find xcodebuild` fails. A simulator build cannot run. No Xcode, simulator, provisioning profile or paid Apple account was downloaded/created. Agree storage placement before setting those up: Xcode/simulator caches can be large and some macOS files use internal storage even with source on ExSSD.

On a compatible Xcode host, use a clean APFS working copy and the pinned Flutter SDK, resolve the lock, then:

```sh
flutter build ios --simulator --no-codesign --build-name=0.1.0 --build-number=2
```

The explicit numeric build name avoids putting the Android alpha suffix in the iOS marketing version. Follow Flutter's reported CocoaPods/Xcode prerequisites; do not add global ATS exceptions to make pairing pass.

Required physical checks: allow and deny Local Network, return from Settings and retry, discover/manual-connect to the same TV, verify fingerprint/PIN, terminate/reopen with actual Keychain storage, background/resume without replay, revoke and reconnect, then use the shared app grid/rows UI. Confirm a wrong certificate blocks credentials. Simulator success alone cannot close these gates.

Issue #13 remains blocked on Xcode and physical iPhone/TV acceptance; this day is source/host complete only. Day 9 can proceed independently against the platform-neutral LAN API.
