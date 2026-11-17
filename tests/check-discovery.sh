#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build="$root/launcher/build/discovery-check"
mkdir -p "$build"
"${JAVA_HOME:?Set JAVA_HOME}/bin/javac" --release 8 -d "$build" \
    "$root/tests/nsd-stubs/android/content/Context.java" \
    "$root/tests/nsd-stubs/android/net/nsd/NsdManager.java" \
    "$root/tests/nsd-stubs/android/net/nsd/NsdServiceInfo.java" \
    "$root/launcher/src/main/java/app/firetvremote/launcher/lan/Discovery.java" \
    "$root/tests/DiscoveryTest.java"
"$JAVA_HOME/bin/java" -cp "$build" app.firetvremote.launcher.lan.DiscoveryTest
