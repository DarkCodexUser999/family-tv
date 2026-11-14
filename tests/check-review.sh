#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes="$root/launcher/build/review-check"
mkdir -p "$classes"
"${JAVA_HOME:?Set JAVA_HOME to JDK 17}/bin/javac" --release 8 -d "$classes" \
  "$root/remote/src/main/java/app/firetvremote/remote/RemoteInput.java" \
  "$root/shared/src/main/java/app/firetvremote/Link.java" \
  "$root/tests/RemoteInputTest.java" "$root/tests/LinkTest.java"
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.remote.RemoteInputTest
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.LinkTest
