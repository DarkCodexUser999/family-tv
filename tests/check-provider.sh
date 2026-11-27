#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes="$root/launcher/build/provider-check"
mkdir -p "$classes"
"${JAVA_HOME:?Set JDK17}/bin/javac" --release 8 -sourcepath "$root/launcher/src/main/java" -cp "$root/launcher/libs/nanohttpd-2.3.1.jar" -d "$classes" "$root/tests/StatusEndpointTest.java" "$root/tests/ProviderSettingsTest.java" "$root/tests/RefreshEndpointTest.java" "$root/tests/ProviderTransportTest.java"
"$JAVA_HOME/bin/java" -cp "$classes" app.firetvremote.launcher.lan.StatusEndpointTest
"$JAVA_HOME/bin/java" -cp "$classes" ProviderSettingsTest
"$JAVA_HOME/bin/java" -cp "$classes" RefreshEndpointTest
"$JAVA_HOME/bin/java" -cp "$classes:$root/launcher/libs/nanohttpd-2.3.1.jar" app.firetvremote.launcher.lan.ProviderTransportTest "$root/launcher/build/lan-check/test.p12" "$classes"
