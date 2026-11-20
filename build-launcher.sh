#!/bin/sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(dirname "$repo_dir")
sdk_dir="${ANDROID_HOME:-$project_dir/tools/android-sdk}"
build_tools="$sdk_dir/build-tools/35.0.0"
android_jar="$sdk_dir/platforms/android-36/android.jar"
module=${1:-launcher}
case "$module" in
    launcher) min_sdk=22; target_sdk=36; version_code=8; version_name=0.8; name=family-tv-debug ;;
    remote) min_sdk=26; target_sdk=34; version_code=2; version_name=0.2; name=family-tv-remote-debug ;;
    *) printf 'Usage: sh build-launcher.sh [launcher|remote]\n' >&2; exit 2 ;;
esac
package="app.firetvremote.$module"
source_dir="$repo_dir/$module/src/main"
build_dir="$repo_dir/$module/build/local"
key_dir="$project_dir/tools/signing"

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export JAVA_HOME

mkdir -p "$build_dir/classes" "$build_dir/dex" "$key_dir"
test -s "$android_jar"

# Gradle normally supplies this package attribute from the namespace.
sed "s/<manifest xmlns:android=/<manifest package=\"$package\" xmlns:android=/" \
    "$source_dir/AndroidManifest.xml" > "$build_dir/AndroidManifest.xml"

if [ "$module" = launcher ] && [ "${FAMILYTV_FOCUS_DEBUG:-0}" = 1 ]; then
    set -- --debug-mode
else
    set --
fi
if [ -d "$source_dir/assets" ]; then
    set -- "$@" -A "$source_dir/assets"
fi
if [ -d "$source_dir/res" ]; then
    "$build_tools/aapt2" compile --dir "$source_dir/res" -o "$build_dir/resources.zip"
    set -- "$@" "$build_dir/resources.zip"
fi
"$build_tools/aapt2" link -I "$android_jar" "$@" \
    --manifest "$build_dir/AndroidManifest.xml" \
    --min-sdk-version "$min_sdk" --target-sdk-version "$target_sdk" \
    --version-code "$version_code" --version-name "$version_name" \
    -o "$build_dir/unsigned.apk"

# Compile recursively, including lan/. A fresh generated class directory prevents stale classes.
classes_dir=$(mktemp -d "$build_dir/classes.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT HUP INT TERM
classpath="$android_jar"
if [ "$module" = launcher ]; then
    (cd "$repo_dir/launcher/libs" && shasum -a 256 -c nanohttpd-2.3.1.jar.sha256)
    classpath="$classpath:$repo_dir/launcher/libs/nanohttpd-2.3.1.jar"
fi
find "$source_dir/java" -name '*.java' ! -name '._*' > "$build_dir/sources.list"
printf '%s\n' "$repo_dir/shared/src/main/java/app/firetvremote/Link.java" >> "$build_dir/sources.list"
# javac argfiles require quoting when the checkout has spaces.
python3 - "$build_dir/sources.list" <<'PYARG'
import sys,json
from pathlib import Path
p=Path(sys.argv[1]);p.write_text('\n'.join(json.dumps(line) for line in p.read_text().splitlines()))
PYARG
"$JAVA_HOME/bin/javac" --release 8 -classpath "$classpath" -d "$classes_dir" "@$build_dir/sources.list"
python3 - "$classes_dir" "$build_dir/project-classes.jar" <<'PYJAR'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import sys
root=Path(sys.argv[1])
with ZipFile(sys.argv[2], 'w', ZIP_DEFLATED) as jar:
    for file in sorted(root.rglob('*.class')):
        if not file.name.startswith('._'):
            jar.write(file, file.relative_to(root).as_posix())
PYJAR
if [ "$module" = launcher ]; then
    set -- "$build_dir/project-classes.jar" "$repo_dir/launcher/libs/nanohttpd-2.3.1.jar"
else
    set -- "$build_dir/project-classes.jar"
fi
bash "$build_tools/d8" --lib "$android_jar" --min-api "$min_sdk" --output "$build_dir/dex" "$@"
(cd "$build_dir/dex" && zip -q "$build_dir/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"

key="$key_dir/family-tv-debug.jks"
if [ ! -f "$key" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$key" -storetype JKS \
        -storepass android -keypass android -alias family-tv-debug \
        -dname 'CN=Family TV Debug' -keyalg RSA -keysize 2048 -validity 10000
fi

apk="$build_dir/$name.apk"
bash "$build_tools/apksigner" sign --ks "$key" --ks-key-alias family-tv-debug \
    --ks-pass pass:android --key-pass pass:android --out "$apk" "$build_dir/aligned.apk"
bash "$build_tools/apksigner" verify --verbose --min-sdk-version "$min_sdk" "$apk"
printf '\nAPK: %s\n' "$apk"
