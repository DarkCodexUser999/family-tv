# Mobile dependency review — 2026-11-18

Direct versions are exact in mobile/pubspec.yaml; all resolved versions and archive SHA-256 checksums are in pubspec.lock. Flutter SDK 3.50.0 is recorded in .flutter-version and .metadata. Review scope: source/manifests, versions, platform requirements, basic secret handling, license texts and build integration. This is not an independent security audit or a claim that no vulnerabilities exist.

| Dependency | Pin | License | Purpose / review |
|---|---|---|---|
| Flutter/Dart | 3.50.0 / 3.14.0, ab2912035e | BSD-style SDK notices | Existing external toolchain; generated Android/iOS hosts, native UI bindings |
| material_ui | 1.1.0 | BSD-3-Clause | Official Flutter Material extraction, generated SDK template version; no added visual framework |
| nsd | 5.0.1 | MIT | Built-in Android NSD/iOS Bonjour bindings, bounded foreground scan, late-start cleanup |
| flutter_secure_storage | 10.0.0 | BSD-3-Clause | Android encryption/Keystore and iOS Keychain; min SDK 24 in package source, app min SDK 26 |
| crypto | 3.0.8 | BSD-3-Clause | SHA-256 of certificate DER before PIN submission |
| flutter_lints | 6.0.0 | BSD-3-Clause | Development-only static checks |
| Tink Android (native transitive) | 1.19.0 in pinned storage plugin | Apache-2.0 | Android encryption implementation; full upstream license bundled as an app asset/LicenseRegistry entry |

Sources inspected: downloaded package archives and LICENSE/pubspec/native build files from https://pub.dev/api/archives/, resolved transitive sources in the SSD Pub cache, and https://raw.githubusercontent.com/tink-crypto/tink-java/v1.19.0/LICENSE. Primary package documentation: https://pub.dev/packages/nsd/versions/5.0.1, https://pub.dev/packages/flutter_secure_storage/versions/10.0.0, https://pub.dev/packages/crypto/versions/3.0.8, https://pub.dev/packages/material_ui/versions/1.1.0.

flutter_secure_storage 11.2.0 was not selected: its Android compile-SDK requirement exceeds this workspace's API 36 toolchain. Version 10.0.0 declares compile SDK 36/Java 17. Exact tested pins take precedence over guessing that the newest package is compatible. Its Darwin implementation requires Keychain entitlements, supplied for the iOS runner. Credentials are read back after writing; physical Keychain/Keystore persistence still requires phones.

nsd_android 2.2.0 uses the legacy Kotlin Gradle plugin; the generated Flutter template compatibility flags allow the current build, but Flutter warns that later versions may remove support. Keep the pinned SDK/package combination; review upgrades before changing it. Federated implementations/transitive packages are locked, not copied into the launcher. None of these phone dependencies is added to the API 22 stick.

Automatic Flutter package notices are available through About → Licenses; the native Tink license is additionally registered lazily. No provider/TMDB key, pairing token, signing key or native test certificate is committed.
