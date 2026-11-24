#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
build="$root/launcher/build/lan-check"
lib="$root/launcher/libs/nanohttpd-2.3.1.jar"
mkdir -p "$build/classes"
(cd "$root/launcher/libs" && shasum -a 256 -c nanohttpd-2.3.1.jar.sha256)
if [ ! -s "$build/test.p12" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -alias test -keyalg RSA -keysize 2048 -storetype PKCS12 \
        -keystore "$build/test.p12" -storepass test-only -keypass test-only \
        -dname CN=localhost -ext SAN=ip:127.0.0.1 -validity 30 >/dev/null 2>&1
fi
"$JAVA_HOME/bin/javac" --release 8 -cp "$lib" -d "$build/classes" \
    "$root/launcher/src/main/java/fi/iki/elonen/NanoWSD.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/ServerLimits.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/HeaderInput.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/BoundedRunner.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/Transport.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/PairingStore.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/PairingWindow.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/PairJson.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/AuthFilter.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/AppLaunch.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/AppsEndpoint.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/LauncherBridge.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/ActionMessage.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/ActionDispatcher.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/EventHub.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/FrameDeadlineInput.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/LanControl.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/Revocations.java" \
    "$root/tests/AppLaunchTest.java" \
    "$root/tests/LanControlTest.java" \
    "$root/tests/LanTransportTest.java" "$root/tests/PairingTest.java" "$root/tests/RemoteBridgeTest.java" "$root/tests/WssTransportTest.java" "$root/tests/RemoteHarness.java"
"$JAVA_HOME/bin/java" -cp "$build/classes:$lib" app.firetvremote.launcher.lan.LanControlTest
"$JAVA_HOME/bin/java" -cp "$build/classes:$lib" app.firetvremote.launcher.lan.LanTransportTest \
    "$build/test.p12" "$build/info.json"

"$JAVA_HOME/bin/java" -cp "$build/classes:$lib" app.firetvremote.launcher.lan.PairingTest

"$JAVA_HOME/bin/java" -cp "$build/classes:$lib" app.firetvremote.launcher.lan.RemoteBridgeTest

"$JAVA_HOME/bin/java" -cp "$build/classes:$lib" app.firetvremote.launcher.lan.WssTransportTest "$build/test.p12" "$build/events"

"$JAVA_HOME/bin/java" -cp "$build/classes:$lib" app.firetvremote.launcher.lan.AppLaunchTest
