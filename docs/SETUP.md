# Setup, build and installation

## 1. Prepare storage and tools

The tested development host is macOS. Use an external SSD for the checkout, SDK and output if internal storage is limited. Keep the drive attached throughout builds.

Suggested layout:

```text
FamilyTVWorkspace/
  repo/                  this checkout
  tools/android-sdk/     Android SDK
  tools/android-user/    Android tool preferences
  tools/signing/         generated private development key (never commit)
```

Install JDK 17 and Android SDK Command-line Tools from their official distributions. Put the command-line tools under `tools/android-sdk/cmdline-tools/latest/` (with its `bin` and `lib` directories). Java must be available to `sdkmanager`.

From the workspace directory:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$PWD/tools/android-sdk"
export ANDROID_USER_HOME="$PWD/tools/android-user"
export GRADLE_USER_HOME="$PWD/tools/gradle-cache"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

Review and accept the SDK licenses when prompted. Tool downloads require internet; launcher artwork does not. JDK installation, macOS temporary files and application caches may still use internal storage. These environment variables do not guarantee zero internal writes.

## 2. Build

From the repository directory, with the environment above still set:

```sh
sh build-launcher.sh
sh build-launcher.sh remote
```

Outputs:

- `launcher/build/local/family-tv-debug.apk`
- `remote/build/local/family-tv-remote-debug.apk`

The direct script uses `javac`, `aapt2`, `d8`, `zipalign` and `apksigner`. Gradle project files are included, but no Gradle wrapper is bundled and the direct script is the documented device-tested build path. On non-macOS hosts set `JAVA_HOME` explicitly; that path has not been verified here.

The script generates a development signing key in the workspace's `tools/signing/` directory if absent. Its `android` password is a development default, not a production secret. Keep the key private and back it up securely. Updates require the same signing key. A different key produces an incompatible update; do not uninstall automatically to work around this, because uninstall removes Family TV's preferences and pairing records.

## 3. Prepare the Fire TV

1. Verify the device model and Fire OS version under Settings → My Fire TV → About.
2. Locate Developer Options. On firmware that hides it, selecting the device name repeatedly under About may reveal it.
3. Enable ADB debugging. Keep the Mac and TV on the same trusted local network.
4. Read the TV's current IPv4 address from its Network page.

Use your device address, not an example address copied from a log:

```sh
TV_IP='REPLACE_WITH_TV_IP'
adb connect "$TV_IP:5555"
```

Approve the debugging prompt on your own TV, then run:

```sh
adb devices
adb -s "$TV_IP:5555" shell getprop ro.product.model
adb -s "$TV_IP:5555" shell getprop ro.build.version.sdk
adb -s "$TV_IP:5555" shell wm size
adb -s "$TV_IP:5555" shell wm density
adb -s "$TV_IP:5555" install -r launcher/build/local/family-tv-debug.apk
adb -s "$TV_IP:5555" shell am start -n app.firetvremote.launcher/.LauncherActivity
```

The APK installs alongside Amazon Home. Normal installation does not enable the experimental accessibility service or grant secure-settings access. Do not disable stock Home.

## 4. Current remote access status

Day 3 extends Day 2's OFF-by-default server with pairing and discovery. With Family TV open, press Menu/Options, enable **Remote access**, and open **Remote connection details** for the private HTTPS address, certificate fingerprint and discovery status. Compare and explicitly pin the displayed SHA-256 fingerprint in your developer client; never globally disable certificate validation.

Choose **Pair a phone** on TV. During the two-minute window, GET `/v1/info` includes `pairingSessionId`. POST `/v1/pair` with `Content-Type: application/json` and a correctly calculated Content-Length (1–1024 UTF-8 bytes):

```json
{"sessionId":"COPY_FROM_INFO","pin":"COPY_TV_PIN","deviceName":"My phone"}
```

The example contains placeholders, not working credentials. Pairing returns a deviceId and one-time plaintext token; phone software must keep it in secure storage. Protected requests use `Authorization: Bearer <token>` over pinned TLS. Tokens must never enter URLs, Git, screenshots or logs. The TV stores only hashes. Five failed guesses close the window; reopening does not reset address throttling. At most eight phones can be paired. Use **Revoke all phone access** and confirm to remove tokens; re-pair the phones you want to keep. No per-phone management UI exists yet.

Discovery uses `_familytv._tcp.` and the actual ephemeral HTTPS port, with no advertised secrets. If unavailable, manually enter the address shown on TV. Switching access OFF stops the service/listener and requests discovery removal. Physical Fire OS discovery/TLS/shutdown acceptance is pending.

There is no Flutter app, working WebSocket connection, remote pad or app/control endpoint yet. The old native APK remains buildable but cannot control this transport; its TCP autostart stays disabled and legacy pairing data remains untouched. See [Day 3 implementation review](DAY_3_REVIEW.md) for tests and limits. Source is published; physical-device acceptance remains pending.

## 5. Configure and verify

Edit asset files as described in [configuration](CONFIGURATION.md), rebuild, and install with `-r`. Existing streaming-app data is untouched. New packages are discovered on launcher startup; simply resuming an existing activity may not refresh the catalog.

A minimal navigation check:

```sh
adb -s "$TV_IP:5555" shell input keyevent 22
adb -s "$TV_IP:5555" shell input keyevent 23
adb -s "$TV_IP:5555" shell dumpsys activity activities
adb -s "$TV_IP:5555" shell input keyevent 4
adb -s "$TV_IP:5555" shell dumpsys activity activities
```

RIGHT is 22, OK is 23, BACK is 4. Verify the foreground package, not just that an intent was sent. Do not infer account login or playback success from activity names.

## Restore and uninstall

Set `ADB` when platform-tools is not in the original sibling `platform-tools` directory:

```sh
ADB="$ANDROID_HOME/platform-tools/adb" sh restore.sh "$TV_IP:5555" --home-only
```

This removes Family TV's accessibility entry and revokes its secure-settings grant if detected, while keeping the app installed. It is intended to preserve other accessibility services.

To also uninstall Family TV (which removes its pairing records/preferences):

```sh
ADB="$ANDROID_HOME/platform-tools/adb" sh restore.sh "$TV_IP:5555"
```

Neither command uninstalls streaming apps. The phone companion can be removed through the phone's app settings. Local artwork under `/sdcard/FamilyTV/` is not removed by the script.

## Troubleshooting

| Symptom | Check |
|---|---|
| ADB unauthorized | Accept the debugging prompt on the TV |
| ADB cannot connect | Current IP, shared network, debugging enabled, network isolation |
| App absent | Installed activity categories, hidden list, full launcher restart |
| App asks for login | The installed app's own account state; launcher does not supply credentials |
| BACK stays inside app | App-specific navigation; universal interception is not implemented |
| HOME opens Amazon Home | Expected on the tested firmware |
| Legacy phone cannot connect | Expected: legacy TCP autostart is disabled; new Flutter controls are not implemented |
| APK signature mismatch | Use the original private signing key; do not clear data as a shortcut |

## Remote build toolchain — Day 1

GitHub CLI 2.104.0 is installed under the external workspace's `tools/github-cli/`. Flutter 3.50.0 / Dart 3.14.0 bootstrap passed; stable checkout is pinned at `ab2912035e160d9cad6306df307576da6128ebdb`; analytics are disabled and evidence is tracked in PROGRESS.md. The phone package is not scaffolded yet, so Flutter analyzer/tests are N/A today.

The workspace SSD is ExFAT. Flutter's macOS hard-link startup lock does not work directly on ExFAT. A new 8 GiB-capacity sparse APFS image (`tools/flutter-tools.sparseimage`, grows with use) stores the tool checkout; mount it at `/Volumes/FamilyTVTools` with `hdiutil attach` before using it. Its backing data stays on ExSSD. This does not reformat the drive. Some macOS preferences and temporary files can still use internal storage. Detach the image before ejecting the SSD.

Protocol validators live in the workspace's `tools/protocol-venv/`. Reproduce with Python 3 and `pip install -r protocol/requirements-dev.txt` inside an SSD-hosted virtual environment. Requirements are development tooling only, never shipped in the launcher. GitHub CLI commands use existing credentials only via a process environment, without committing or writing a token to this repository. Daily issue: https://github.com/DarkCodexUser999/family-tv/issues/1.
