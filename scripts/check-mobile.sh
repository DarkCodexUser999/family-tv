#!/bin/sh
# Pass real test sources explicitly: ExFAT AppleDouble ._ files are not Dart.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sh "$root/scripts/flutter.sh" analyze
sh "$root/scripts/flutter.sh" test test/widget_test.dart test/tls_test.dart test/preview_test.dart test/remote_test.dart test/apps_test.dart test/android_flow_test.dart test/ios_parity_test.dart test/rows_test.dart test/hidden_test.dart test/artwork_test.dart
