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

set --
if [ -d "$source_dir/assets" ]; then
    set -- -A "$source_dir/assets"
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

"$JAVA_HOME/bin/javac" --release 8 -classpath "$android_jar" -d "$build_dir/classes" \
    "$source_dir/java/app/firetvremote/$module/"*.java \
    "$repo_dir/shared/src/main/java/app/firetvremote/Link.java"
sh "$build_tools/d8" --lib "$android_jar" --min-api "$min_sdk" --output "$build_dir/dex" \
    "$build_dir/classes/app/firetvremote/Link.class" \
    "$build_dir/classes/app/firetvremote/$module/"*.class
(cd "$build_dir/dex" && zip -q "$build_dir/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"

key="$key_dir/family-tv-debug.jks"
if [ ! -f "$key" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$key" -storetype JKS \
        -storepass android -keypass android -alias family-tv-debug \
        -dname 'CN=Family TV Debug' -keyalg RSA -keysize 2048 -validity 10000
fi

apk="$build_dir/$name.apk"
sh "$build_tools/apksigner" sign --ks "$key" --ks-key-alias family-tv-debug \
    --ks-pass pass:android --key-pass pass:android --out "$apk" "$build_dir/aligned.apk"
sh "$build_tools/apksigner" verify --verbose --min-sdk-version "$min_sdk" "$apk"
printf '\nAPK: %s\n' "$apk"
