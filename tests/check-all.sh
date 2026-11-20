#!/bin/sh
# Common local/CI gate. Android SDK is only required for the separate APK builds.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python3 "$root/tests/check-catalog.py"
python3 "$root/tests/check-provider-secrets.py"
sh "$root/tests/check-launcher-policy.sh"
sh "$root/tests/check-rows.sh"
sh "$root/tests/check-navigation.sh"
python3 "$root/tests/check-focus-trace.py"
python3 "$root/tests/check-focus-window.py"
sh "$root/tests/check-review.sh"
sh "$root/tests/check-lan.sh"
sh "$root/tests/check-discovery.sh"
for script in "$root/build-launcher.sh" "$root/restore.sh" "$root/tests/"*.sh; do
    sh -n "$script"
done
python3 - "$root" <<'PY'
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(sys.argv[1])
config = json.loads((root / 'launcher/src/main/assets/rows.json').read_text())
rows = config['rows']
if not isinstance(rows, list) or not 1 <= len(rows) <= 4:
    raise ValueError('rows.json must contain one to four rows')
for row in rows:
    packages = row['packages']
    if not isinstance(packages, list) or not all(isinstance(p, str) and p.strip() for p in packages):
        raise ValueError('Each row requires a list of nonempty package identifiers')
for path in [root / 'launcher/src/main/AndroidManifest.xml', root / 'remote/src/main/AndroidManifest.xml',
             root / 'launcher/src/main/res/xml/home_service.xml']:
    ET.parse(path)
print('PASS: row configuration, manifests and shell syntax')
PY
