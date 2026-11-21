# Automated checks

The **Build and regression checks** GitHub Actions workflow runs on pushes, pull requests and manual dispatch. It runs the same local checks, installs SDK 36 / Build Tools 35.0.0, builds the launcher and phone companion, and verifies their development signatures on an Ubuntu runner using JDK 17.

## Run the same checks locally

On the Mac, from the repository directory:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
sh tests/check-all.sh
sh build-launcher.sh
sh build-launcher.sh remote
```

The test script requires JDK 17 and Python 3. APK builds additionally require the SDK described in SETUP.md. Generated files remain in the ignored build folders on your project drive.

## Read the result

Open the repository's **Actions** tab, select **Build and regression checks**, and open a run. Green means all listed checks/builds passed; red means a step failed. Expand that step to see the error. The final step summarizes exactly what was validated.

The README badge links to this workflow. To rerun manually, choose **Run workflow** on its Actions page. A new push on the same branch cancels an older in-progress run.

## Scope and credentials

The workflow has read-only repository permissions, uses commit-pinned official checkout/Java actions, and does not use the maintainer's signing key or repository secrets. It creates a temporary development key on the runner and does not upload APKs or keys. A runner APK is not a signed update for your locally installed build.

CI is a source/build check, not a device check. It does not verify Android rendering, Fire OS compatibility, HOME/BACK takeover, phone pairing UX, streaming playback, PSS or input latency. Those still need the actual hardware. If Actions is disabled or account limits prevent a run, the code may be pushed without a completed check; inspect the run status rather than assuming success.

References: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), [SDK package manager](https://developer.android.com/tools/sdkmanager).

## SDK tool portability

The D8 and apksigner SDK wrappers declare Bash as their interpreter. The build script invokes them with `bash`, rather than forcing Ubuntu's POSIX `sh` to parse Bash arrays. The first CI run exposed this Mac/Linux difference; the regression checks passed before the APK build encountered it.

## Phone companion and interoperability

The separate Ubuntu mobile job checks out the Flutter revision recorded in `mobile/.metadata`, resolves the committed dependency lock, runs the analyzer and Flutter tests, and builds an Android debug APK. Its interoperability test starts the real Java HTTPS/WSS transport, connects the Dart client with a pinned certificate, and exercises command delivery and foreground gating. A runner without a private IPv4 address fails this job. Locally, setting `FAMILYTV_REQUIRE_INTEROP=1` requires the same coverage.

```sh
sh scripts/flutter.sh pub get --enforce-lockfile
sh scripts/check-mobile.sh
FAMILYTV_REQUIRE_INTEROP=1 sh scripts/check-remote-interop.sh
sh scripts/flutter.sh build apk --debug
```

The native job runs the mocked ADB restore fixtures in `tests/check-all.sh`. After building both native APKs, it runs `python3 tests/check-build-parity.py --apks` to inspect their packaged metadata, compiled class versions and launcher contents. GitHub run results and physical-device results are tracked separately.
