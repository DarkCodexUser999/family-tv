"""Run restore twice against an allowlisted, stateful fake ADB. No device access."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

PKG = "app.firetvremote.launcher"
FULL = PKG + "/" + PKG + ".HomeService"
SHORT = PKG + "/.HomeService"
OTHER = "reader.pkg/.Reader"
LIST = "enabled_accessibility_services"
GLOBAL = "accessibility_enabled"
GRANT = "android.permission.WRITE_SECURE_SETTINGS"
ROOT = Path(__file__).resolve().parents[1]

def mock(state, args):
    assert args[:2] == ["-s", "test-stick"]
    cmd = args[2:]
    if cmd == ["shell", "pm", "list", "packages", PKG]:
        if state.get("pm_error"):
            print("Error: package manager unavailable")
        elif state["installed"]:
            print("package:" + PKG)
    elif cmd == ["shell", "dumpsys", "package", PKG]:
        assert state["installed"]
        if state.get("dump_denied"):
            print("Permission Denial")
            return 1
        print(f"Package [{PKG}] (abc):")
        print("  requested permissions:")
        print("    " + GRANT)
        if state["grant"]:
            if state.get("legacy_grant"):
                print("  grantedPermissions:")
                print("    " + GRANT)
            else:
                print(GRANT + ": granted=true")
    elif cmd[:4] == ["shell", "settings", "get", "secure"]:
        name = cmd[4]
        assert name in (LIST, GLOBAL)
        if name == LIST:
            state["reads"] += 1
            if state.get("deny"):
                print("Permission Denial")
                return 1
            if state.get("race") and state["reads"] == 2:
                state[LIST] += ":" + OTHER
        if name == LIST and state.get("malformed"):
            print("Permission Denial")
        else:
            print(state[name])
    else:
        state["writes"].append(cmd)
        if state.pop("ignored_write", False):
            return 0
        if cmd[:4] == ["shell", "settings", "put", "secure"] and cmd[4] == LIST:
            state[LIST] = cmd[5].strip("'")
        elif cmd == ["shell", "settings", "put", "secure", GLOBAL, "0"]:
            assert state[LIST] in (FULL, SHORT, "null")
            state[GLOBAL] = "0"
        elif cmd == ["shell", "settings", "delete", "secure", LIST]:
            if state.pop("interrupt", False):
                return 1
            state[LIST] = "null"
        elif cmd == ["shell", "pm", "revoke", PKG, GRANT]:
            state["grant"] = False
        elif cmd == ["uninstall", PKG]:
            assert state["installed"]
            state["installed"] = False
            print("Success")
        else:
            raise AssertionError("Unexpected mutation: " + str(cmd))
    return 0

def run_tests():
    with tempfile.TemporaryDirectory() as tmp:
        data = Path(tmp) / "state"
        adb = Path(tmp) / "adb"
        adb.write_text(f'#!/bin/sh\nexec "{sys.executable}" "{__file__}" mock "$@"\n')
        adb.chmod(0o700)
        env = dict(os.environ, ADB=str(adb), RESTORE_TEST_STATE=str(data))
        def twice(value, expected, installed=True, home=True, fail=False, **flags):
            state = {LIST:value, GLOBAL:"1", "installed":installed, "grant":installed,
                     "reads":0, "writes":[], **flags}
            data.write_text(json.dumps(state))
            args = ["sh", str(ROOT/"restore.sh"), "test-stick"] + (["--home-only"] if home else [])
            for attempt in range(2):
                result = subprocess.run(args, env=env, capture_output=True, text=True)
                if fail and attempt == 0:
                    assert result.returncode != 0, (value,flags,result)
                else:
                    assert result.returncode == 0, (value,flags,result)
                got = json.loads(data.read_text())
                if attempt == 0 and fail:
                    if any(flags.get(key) for key in ("deny", "dump_denied", "race", "malformed", "pm_error")):
                        assert not got["writes"]
                    got.update(deny=False, dump_denied=False, malformed=False, pm_error=False)
                    data.write_text(json.dumps(got))
                else:
                    assert got[LIST] == expected and got["installed"] == (installed and home), got
                    if expected == "null" and value in (FULL, SHORT):
                        assert got[GLOBAL] == "0"
                    else:
                        assert got[GLOBAL] == "1"
                    if home and installed: assert not got["grant"]
                    if attempt and not fail: assert got["writes"] == previous
                previous = list(got["writes"])
        twice(FULL, "null", installed=False)
        twice(OTHER+":"+SHORT+":"+FULL, OTHER)
        twice("null","null",installed=False)
        twice(SHORT,"null",home=False)
        twice(OTHER, OTHER, fail=True, deny=True)
        twice(FULL, "null", fail=True, interrupt=True)
        twice(FULL, OTHER, fail=True, race=True)
        twice(OTHER, OTHER, fail=True, dump_denied=True)
        twice(OTHER, OTHER, fail=True, pm_error=True)
        twice(OTHER, OTHER, fail=True, malformed=True)
        twice(SHORT+":"+OTHER, OTHER, fail=True, ignored_write=True)
        twice(OTHER, OTHER, legacy_grant=True)
    print("PASS: restore mock, stale/mixed/null lists, home-only/uninstall, denied reads, races, interruption and reruns")

if __name__ == "__main__":
    if sys.argv[1:2] == ["mock"]:
        path = Path(os.environ["RESTORE_TEST_STATE"])
        state = json.loads(path.read_text())
        code = mock(state, sys.argv[2:])
        path.write_text(json.dumps(state))
        sys.exit(code)
    run_tests()
