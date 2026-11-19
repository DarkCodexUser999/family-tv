#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then JAVA_HOME=$(/usr/libexec/java_home -v 17);export JAVA_HOME;fi
sh "$root/tests/check-lan.sh"
sh "$root/scripts/flutter.sh" test test/remote_interop_test.dart
