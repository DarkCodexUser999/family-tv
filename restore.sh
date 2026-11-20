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
serial=$1
list=enabled_accessibility_services
global=accessibility_enabled
grant=android.permission.WRITE_SECURE_SETTINGS
device() { "$adb" -s "$serial" "$@"; }
die() { printf '%s\n' "$1" >&2; exit 1; }

# Older adb shell versions can return success with an error on stdout.
read_device() {
    raw=$(device "$@" 2>&1) || die "ADB read failed: $*"
    printf '%s\n' "$raw" | tr -d '\r'
}
read_list() {
    value=$(read_device shell settings get secure "$list") || exit 1
    case "$value" in null|'') printf '\n'; return ;; esac
    printf '%s\n' "$value" | awk -F: '
        NR != 1 { exit 1 }
        { for (i=1; i<=NF; ++i) if ($i !~ /^[a-zA-Z0-9_.]+\/[.a-zA-Z0-9_$]+$/) exit 1 }
    ' || die 'Invalid accessibility service list.'
    printf '%s\n' "$value"
}
read_global() {
    value=$(read_device shell settings get secure "$global") || exit 1
    case "$value" in 0|1|null|'') printf '%s\n' "$value" ;; *) die 'Invalid accessibility flag.' ;; esac
}
check_list() {
    actual=$(read_list) || exit 1
    [ "$actual" = "$1" ] || die 'Accessibility services changed. Inspect settings and rerun restore.'
}
is_installed() {
    packages=$(read_device shell pm list packages "$package") || exit 1
    printf '%s\n' "$packages" | awk -v target="package:$package" '
        /^$/ { next } !/^package:[A-Za-z0-9_.]+$/ { bad=1 } $0 == target { found=1 }
        END { if (bad) exit 1; print found ? "yes" : "no" }' || die 'Invalid package list.'
}
read_grant() {
    dump=$(read_device shell dumpsys package "$package") || exit 1
    printf '%s\n' "$dump" | awk -v pkg="$package" -v grant="$grant:" '
        !seen && $1 == "Package" && $2 == "[" pkg "]" { seen=inside=1; next }
        $1 == "Package" { inside=0 }
        inside && $1 == grant && $2 ~ /^granted=true(,|$)/ { held=1 }
        inside && $1 == "grantedPermissions:" { legacy=1; next }
        /:[[:space:]]*$/ { legacy=0 }
        inside && legacy && NF == 1 && $1 ":" == grant { held=1 }
        /Permission Denial|SecurityException/ { bad=1 }
        END { if (!seen || bad) exit 1; print held ? "yes" : "no" }' || die 'Invalid package permission dump.'
}

installed=$(is_installed)
held=no
if [ "$installed" = yes ]; then held=$(read_grant); fi
enabled=$(read_list)
case ":$enabled:" in
    *":$service:"*|*":$short_service:"*)
        remaining=$(printf '%s\n' "$enabled" | awk -F: -v full="$service" -v short="$short_service" '
            { for (i=1; i<=NF; i++) if ($i != full && $i != short) {
                if (count++) printf ":"
                printf "%s", $i
            } }')
        if [ -n "$remaining" ]; then
            check_list "$enabled"
            # Quote for the remote shell, including class names containing dollar signs.
            device shell settings put secure "$list" "'$remaining'"
        else
            previous=$(read_global)
            if [ "$previous" = 1 ]; then
                check_list "$enabled"
                device shell settings put secure "$global" 0
                after=$(read_global)
                [ "$after" = 0 ] || die 'Failed to verify accessibility flag.'
            fi
            check_list "$enabled"
            device shell settings delete secure "$list"
        fi
        check_list "$remaining" ;;
esac

if [ "$held" = yes ]; then
    device shell pm revoke "$package" "$grant"
    held=$(read_grant)
    [ "$held" = no ] || die 'Failed to verify grant removal.'
fi
if [ "$installed" = yes ] && [ "${2:-}" != "--home-only" ]; then
    device uninstall "$package"
    installed=$(is_installed)
    [ "$installed" = no ] || die 'Failed to verify Family TV uninstall.'
fi
