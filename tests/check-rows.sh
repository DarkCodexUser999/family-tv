#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes="$root/launcher/build/row-check"
mkdir -p "$classes"
"${JAVA_HOME:?Set JAVA_HOME to JDK 17}/bin/javac" --release 8 -d "$classes" \
  "$root/launcher/src/main/java/app/firetvremote/launcher/RowPresentation.java" \
  "$root/tests/RowPresentationTest.java"
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.launcher.RowPresentationTest
