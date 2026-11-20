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
| Connectivity | Default-off HTTPS pairing/discovery; Day 5 candidate adds pinned WSS controls inside Family TV |
| Build | JDK 17, Android SDK 36, Build Tools 35.0.0; direct shell build |

## What it does

- Discovers installed apps through `LEANBACK_LAUNCHER`, falling back to `LAUNCHER` activities.
- Opens installed apps without clearing their data or replacing their accounts.
- Presents banner-based tiles in configurable rows with D-pad navigation, per-row selection memory and saved last-app focus (latest focus changes await TV verification).
- Uses pre-rendered artwork, a reusable small backdrop cache, clear focus outlines and simple scale/translation/alpha effects.
- Supports local tile and backdrop overrides, with generated artwork as a fallback.
- Hides unwanted tiles through an editable asset list without uninstalling their packages.
- Supports per-app task flags rather than forcing one launch strategy on every app.
- Includes legacy Android companion source and Flutter Android/iOS source; Day 5 local candidate adds a foreground-only pad. Legacy TCP autostart is disabled.

## What it does not promise

- HOME interception did **not** activate on the tested Fire OS build. The experimental service remains in source but is not enabled by the normal install steps.
- BACK is controlled partly by the launched app. A single press does not consistently return to Family TV.
- The optional in-process server supports pairing and, in the Day 5 candidate, foreground-only semantic controls. It cannot navigate Netflix, YouTube or other apps system-wide.
- Volume/mute buttons are disabled. TV power and IR volume control are not implemented.
- No bundled streaming apps, subscription access, account bypass, content scraping or app-data migration.
- No proven frame-time or speed advantage over Amazon Home.

## Experimental remote transport

Day 5 adds authenticated WSS state/events and a phone D-pad with no input replay, published after owner approval. The default-off server controls Family TV only. Existing native companion remains incompatible with the new transport. See [Day 5 review](docs/DAY_5_REVIEW.md) for setup/evidence and pending physical Fire OS TLS/discovery/PSS/latency gates.

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
remote/            Legacy native Android phone companion
mobile/            Flutter Android/iOS pairing and Family TV pad
protocol/          Versioned contracts, fixtures and security notes
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

## Phone app candidate

Day 5 adds a remote pad to the Flutter pairing source under mobile/. Exact TLS certificate pinning and saved credentials authenticate the connection; D-pad/OK/Home/Back/Options work only while Family TV is open. No other-app input, TV volume or power. See [mobile setup](mobile/README.md), [local evidence](docs/DAY_5_REVIEW.md), and [dependencies](docs/MOBILE_DEPENDENCIES.md). Source is published; physical phone/TV, memory/latency and iOS build acceptance remain pending.

## Movie and series artwork

Family TV reads the optional TMDB credential from `/sdcard/FamilyTV/tmdb_key.txt` on the stick. Supply a TMDB API key or API Read Access Token there. Keep this file off GitHub. The launcher resolves streaming providers for India and downloads artwork in the background. Local package artwork overrides take priority. A tinted dark gradient covers missing artwork.

This product uses the TMDB API but is not endorsed or certified by TMDB.
