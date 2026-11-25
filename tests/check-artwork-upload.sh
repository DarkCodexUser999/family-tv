#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes="$root/launcher/build/upload-check"
mkdir -p "$classes"
"${JAVA_HOME:?Set JDK17}/bin/javac" --release 8 -d "$classes" \
 "$root/launcher/src/main/java/app/firetvremote/launcher/lan/AppsEndpoint.java" \
 "$root/launcher/src/main/java/app/firetvremote/launcher/lan/UploadValidator.java" \
 "$root/launcher/src/main/java/app/firetvremote/launcher/lan/MediaJobs.java" \
 "$root/tests/UploadValidatorTest.java" "$root/tests/ArtworkUploadTest.java"
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.launcher.lan.UploadValidatorTest
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.launcher.lan.ArtworkUploadTest "$classes"

if [ -s "$root/launcher/build/lan-check/test.p12" ]; then
 "$JAVA_HOME/bin/javac" --release 8 -sourcepath "$root/launcher/src/main/java" -cp "$root/launcher/libs/nanohttpd-2.3.1.jar" -d "$classes" "$root/tests/ArtworkTransportTest.java"
 "$JAVA_HOME/bin/java" -cp "$classes:$root/launcher/libs/nanohttpd-2.3.1.jar" app.firetvremote.launcher.lan.ArtworkTransportTest "$root/launcher/build/lan-check/test.p12" "$classes"
fi
