#!/bin/sh
# Undo Family TV Home handling, then optionally remove our launcher.
set -eu
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ] || { [ "$#" -eq 2 ] && [ "$2" != "--home-only" ]; }; then
    printf 'Usage: sh restore.sh <adb-device-serial> [--home-only]\n' >&2
    exit 2
fi
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
adb="${ADB:-$root/../platform-tools/adb}"
package=app.firetvremote.launcher
service="$package/$package.HomeService"
short_service="$package/.HomeService"
installed=$("$adb" -s "$1" shell pm list packages "$package")
if printf '%s\n' "$installed" | tr -d '\r' | grep -Fxq "package:$package"; then
    enabled=$("$adb" -s "$1" shell settings get secure enabled_accessibility_services | tr -d '\r')
    remaining=$(printf '%s\n' "$enabled" | awk -F: -v full="$service" -v short="$short_service" '
        { for (i=1; i<=NF; i++) if ($i != full && $i != short && $i != "") {
            if (count++) printf ":"
            printf "%s", $i
        } }')
    if [ "$enabled" != "$remaining" ]; then
        if [ -n "$remaining" ]; then
            case "$remaining" in *[!a-zA-Z0-9_./:-]*) exit 1 ;; esac
            "$adb" -s "$1" shell settings put secure enabled_accessibility_services "$remaining"
        else
            "$adb" -s "$1" shell settings delete secure enabled_accessibility_services
            "$adb" -s "$1" shell settings put secure accessibility_enabled 0
        fi
    fi
    if "$adb" -s "$1" shell dumpsys package "$package" |
            grep -Eq 'android\.permission\.WRITE_SECURE_SETTINGS: granted=true'; then
        "$adb" -s "$1" shell pm revoke "$package" android.permission.WRITE_SECURE_SETTINGS
    fi
    if [ "${2:-}" != "--home-only" ]; then
        "$adb" -s "$1" uninstall "$package"
    fi
else
    printf '%s is already absent.\n' "$package"
fi
