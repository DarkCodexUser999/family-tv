#!/bin/sh
# SSD-first local Flutter wrapper. A mounted FamilyTVTools image avoids ExFAT tool locks.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tools="$(dirname "$root")/tools"
if [ -d /Volumes/FamilyTVTools/flutter ]; then
  flutter_bin=/Volumes/FamilyTVTools/flutter/bin/flutter
  cache=/Volumes/FamilyTVTools
else
  flutter_bin=${FLUTTER_BIN:-flutter}
  cache="$tools/mobile-cache"
fi
if [ -d "$cache/ndk/android-ndk-r28c" ]; then export FAMILYTV_NDK_HOME="$cache/ndk/android-ndk-r28c"; fi
export PUB_CACHE="${PUB_CACHE:-$cache/pub-cache}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$cache/gradle-cache}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$tools/android-user}"
if [ -d /Volumes/FamilyTVTools/android-sdk ]; then
  export ANDROID_HOME=/Volumes/FamilyTVTools/android-sdk
elif [ -d "$tools/android-sdk" ]; then export ANDROID_HOME="${ANDROID_HOME:-$tools/android-sdk}"; fi
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then JAVA_HOME=$(/usr/libexec/java_home -v 17); export JAVA_HOME; fi
cd "${FAMILYTV_MOBILE_DIR:-$root/mobile}"
exec "$flutter_bin" "$@"
