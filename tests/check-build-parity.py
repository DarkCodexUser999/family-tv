"""Check native Gradle/shell settings and, with --apks, the packaged outputs."""
import argparse
import os
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", ROOT.parent / "tools/android-sdk"))


def require(left, right, field):
    if left != right:
        raise SystemExit(f"{field} mismatch: {left!r} versus {right!r}")


def one(pattern, text, field):
    values = re.findall(pattern, text, re.MULTILINE)
    if len(values) != 1:
        raise SystemExit(f"Expected one {field}, found {values}")
    return values[0]


def check(module, check_apk):
    props = subprocess.check_output(
        ["sh", ROOT / "build-launcher.sh", "--print-config", module], text=True
    )
    config = dict(line.split("=", 1) for line in props.splitlines())
    gradle = (ROOT / module / "build.gradle").read_text()
    for key in ("applicationId", "compileSdk", "minSdk", "targetSdk", "versionCode",
                "versionName", "buildToolsVersion"):
        value = one(rf"""^\s*{key}\s+['"]?([\w.]+)['"]?\s*$""", gradle, key)
        require(value, config[key], f"{module} {key}")
    for key in ("sourceCompatibility", "targetCompatibility"):
        version = one(rf"^\s*{key}\s+JavaVersion\.VERSION_(?:1_)?(\d+)\s*$", gradle, key)
        require(version, config["javaRelease"], f"{module} {key}")
    if not check_apk:
        return

    directory = ROOT / module / "build/local"
    apk = directory / ("family-tv-debug.apk" if module == "launcher" else "family-tv-remote-debug.apk")
    aapt = SDK / "build-tools" / config["buildToolsVersion"] / "aapt2"
    badging = subprocess.check_output([aapt, "dump", "badging", apk], text=True)
    package = one(r"^package: (.+)$", badging, "package")
    for key, field in (("name", "applicationId"), ("versionCode", "versionCode"), ("versionName", "versionName"),
                       ("compileSdkVersion", "compileSdk")):
        require(one(rf"\b{key}='([^']+)'", package, key), config[field], f"{apk.name} {key}")
    for tag, field in (("minSdkVersion", "minSdk"), ("targetSdkVersion", "targetSdk")):
        require(one(rf"^{tag}:\s*'(\d+)'", badging, tag), config[field], f"{apk.name} {field}")
    with ZipFile(directory / "project-classes.jar") as classes:
        versions = {int.from_bytes(classes.read(name)[6:8], "big")
                    for name in classes.namelist() if name.endswith(".class")}
        require(versions, {44 + int(config["javaRelease"])}, f"{module} class file version")
    with ZipFile(apk) as archive:
        if "classes.dex" not in archive.namelist():
            raise SystemExit(f"{apk.name} missing classes.dex")
        if module == "launcher" and any(name.startswith(("lib/", "assets/flutter_assets/"))
                                        for name in archive.namelist()):
            raise SystemExit("Unexpected native library or Flutter assets in launcher")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apks", action="store_true", help="inspect both already-built APKs")
    args = parser.parse_args()
    for module in ("launcher", "remote"):
        check(module, args.apks)
    print("PASS: native build configuration parity" + (" and both compiled APKs" if args.apks else ""))
