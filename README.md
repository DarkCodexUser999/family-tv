# Family TV

[![Build and regression checks](https://github.com/DarkCodexUser999/family-tv/actions/workflows/checks.yml/badge.svg)](https://github.com/DarkCodexUser999/family-tv/actions/workflows/checks.yml)

A lightweight, family-oriented Fire TV launcher and Android companion remote, designed around an older Fire TV Stick and a small living-room television.

**Status: experimental MVP.** The launcher is usable as a normal app. It is not a proven Amazon Home replacement, and the companion remote is not a system-wide remote. See [known limitations and test results](docs/TESTING.md) before installing.

## At a glance

| Component | Baseline |
|---|---|
| TV launcher | Fire TV Stick 2nd Gen; Fire OS 5.2.9.5; Android 5.1 / API 22 |
| Hardware tested | 32-bit ARM, approximately 1 GB RAM; limited free memory |
| Phone remote | Android 8.0 / API 26 or newer; broader device coverage remains unverified |
| Implementation | Java, native Android Views; no Compose or native libraries; pinned NanoHTTPD transport |
| Connectivity | Default-off HTTPS info, PIN pairing and discovery; phone controls planned |
| Build | JDK 17, Android SDK 36, Build Tools 35.0.0; direct shell build |

## What it does

- Discovers installed apps through `LEANBACK_LAUNCHER`, falling back to `LAUNCHER` activities.
- Opens installed apps without clearing their data or replacing their accounts.
- Presents banner-based tiles in configurable rows with D-pad navigation, per-row selection memory and saved last-app focus (latest focus changes await TV verification).
- Uses pre-rendered artwork, a reusable small backdrop cache, clear focus outlines and simple scale/translation/alpha effects.
- Supports local tile and backdrop overrides, with generated artwork as a fallback.
- Hides unwanted tiles through an editable asset list without uninstalling their packages.
- Supports per-app task flags rather than forcing one launch strategy on every app.
- Includes legacy Android companion source and a Day 4 Flutter pairing preview (Android/iOS source); controls remain planned and legacy TCP autostart is disabled in the current launcher.

## What it does not promise

- HOME interception did **not** activate on the tested Fire OS build. The experimental service remains in source but is not enabled by the normal install steps.
- BACK is controlled partly by the launched app. A single press does not consistently return to Family TV.
- The optional in-process server exposes info and PIN pairing; phone controls remain planned. It cannot navigate Netflix, YouTube or other apps system-wide.
- Volume/mute buttons are disabled. TV power and IR volume control are not implemented.
- No bundled streaming apps, subscription access, account bypass, content scraping or app-data migration.
- No proven frame-time or speed advantage over Amazon Home.

## Experimental remote transport

The published Day 2 transport replaces legacy TCP autostart with an OFF-by-default HTTPS info server. Day 3 adds expiring PIN pairing, hashed phone credentials, bearer checks, discovery and revoke-all settings. New Flutter controls are not implemented; the old native companion cannot control this transport. See [Day 3 review](docs/DAY_3_REVIEW.md) for setup and honest validation limits. Physical Fire OS TLS/discovery/memory/latency acceptance remains pending.

## Start here

1. Read [setup, build and installation](docs/SETUP.md).
2. Customize [rows, hidden tiles and artwork](docs/CONFIGURATION.md).
3. Review [architecture and pairing](docs/ARCHITECTURE.md).
4. Check [development history and device observations](docs/TESTING.md).
5. See [automated checks](docs/CI.md) for build and regression results.
6. Use [restore instructions](docs/SETUP.md#restore-and-uninstall) if you want to remove Family TV.

## Repository layout

```text
launcher/          TV activity, app catalog, artwork, local remote server
remote/            Android phone companion
shared/            HMAC challenge-response protocol
launcher/src/main/assets/
  rows.json        Row membership
  hidden-apps.txt  Launcher-only exclusions
  own-task-apps.txt  Per-app task strategy
build-launcher.sh  Direct build and development signing
restore.sh         Remove Family TV Home handling / uninstall Family TV
phase-2-launch-audit.md  Earlier, historical device trials
docs/              Setup, design, configuration and verification notes
```

## Development principles

Keep API 22 compatibility and memory use in mind. Do not disable Amazon launcher, Settings, System UI, Bluetooth or input packages. Do not clear streaming-app data to diagnose navigation. Make changes reversible, preserve user configuration, and validate using physical-style D-pad/OK events as well as activity inspection.

The project intentionally keeps SDKs, signing keys, APKs, device logs and local backups outside version control. A public repository does not contain the original developer's signing key or device configuration.

## References and ownership

The project brief used FireFrame, dumbtv, firedtvlauncher, FTVLaunchX, tvOS-Launcher, AerioTV-Android and Debrify as launcher/design research references. See [reference notes](docs/REFERENCES.md). These are independent projects; this repository does not claim affiliation with Apple, Amazon or streaming providers. App logos/banners are loaded from installed packages at runtime and belong to their respective owners.

**License:** no redistribution license has been selected for this repository yet. Public source visibility is not an open-source license. Do not assume referenced projects share one license or that their code can be copied here without review.

## Phone app pairing preview

Day 4 adds a shared Flutter pairing app under mobile/ (published pairing preview). It discovers or manually addresses Family TV, verifies its certificate fingerprint, enters the TV PIN and saves credentials securely. It does not provide remote controls yet. See [mobile setup](mobile/README.md), [review and tests](docs/DAY_4_REVIEW.md), and [dependency review](docs/MOBILE_DEPENDENCIES.md). Physical phone/TV and iOS build acceptance remain pending.
