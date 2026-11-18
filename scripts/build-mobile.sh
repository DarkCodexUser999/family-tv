#!/bin/sh
# Build from real sources on APFS: Android tools treat ExFAT AppleDouble files as resources.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
workspace_root=${FAMILYTV_BUILD_ROOT:-/Volumes/FamilyTVTools}
if [ ! -d "$workspace_root" ]; then
    printf 'Mount the SSD-backed FamilyTVTools image or set FAMILYTV_BUILD_ROOT to an APFS build folder.\n' >&2
    exit 1
fi
workdir=$(mktemp -d "$workspace_root/family-tv-mobile-build.XXXXXX")
trap 'rm -rf "$workdir"' EXIT HUP INT TERM
python3 - "$root/mobile" "$workdir" <<'PY'
import shutil,sys
from pathlib import Path
source,dest=map(Path,sys.argv[1:])
for folder in ['android','lib','assets']:
    if not (source/folder).exists(): continue
    for file in (source/folder).rglob('*'):
        rel=file.relative_to(source)
        if any(p.startswith('._') or p in {'.gradle','build','.cxx','local.properties'} for p in rel.parts): continue
        if file.is_file() and not file.is_symlink():
            target=dest/rel;target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(file,target)
            if target.name=='gradlew':target.chmod(0o755)
for name in ['pubspec.yaml','pubspec.lock','.metadata','analysis_options.yaml']:
    shutil.copyfile(source/name,dest/name)
PY
FAMILYTV_MOBILE_DIR="$workdir" sh "$root/scripts/flutter.sh" pub get --offline
FAMILYTV_MOBILE_DIR="$workdir" sh "$root/scripts/flutter.sh" build apk --debug
output="$root/mobile/build/app/outputs/flutter-apk"
mkdir -p "$output"
cp "$workdir/build/app/outputs/flutter-apk/app-debug.apk" "$output/app-debug.apk"
printf 'APK copied to %s/app-debug.apk\n' "$output"
