#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build="$root/launcher/build/policy-check"
mkdir -p "$build"
"$JAVA_HOME/bin/javac" --release 8 -d "$build" \
 "$root/tests/route-stubs/android/content/Intent.java" \
 "$root/tests/route-stubs/android/content/ComponentName.java" \
 "$root/launcher/src/main/java/app/firetvremote/launcher/LaunchRoute.java" "$root/tests/LaunchRouteTest.java"
"$JAVA_HOME/bin/java" -cp "$build" app.firetvremote.launcher.LaunchRouteTest
