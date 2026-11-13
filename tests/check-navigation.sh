#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes="$root/launcher/build/navigation-check"
mkdir -p "$classes"
"${JAVA_HOME:?Set JAVA_HOME to JDK 17}/bin/javac" --release 8 -d "$classes" \
  "$root/launcher/src/main/java/app/firetvremote/launcher/NavigationTiming.java" \
  "$root/tests/NavigationTimingTest.java"
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.launcher.NavigationTimingTest
