"""Check the shipped launcher policy, including its recovery copy."""
import json
import re
from pathlib import Path

assets = Path(__file__).resolve().parents[1] / "launcher/src/main/assets"
def package_list(name):
    return [line.strip() for line in (assets / name).read_text().splitlines()
            if line.strip() and not line.strip().startswith("#")]

def validate(packages):
    if len(packages) != len(set(packages)) or not all(
        re.fullmatch(r"[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+", p, re.ASCII) for p in packages
    ):
        raise ValueError("Duplicate or invalid package in launcher configuration")

hidden = package_list("hidden-apps.txt")
validate(hidden)
catalog = (assets.parent / "java/app/firetvremote/launcher/AppCatalog.java").read_text()
fallback = catalog.split('return readPackages(context, "hidden-apps.txt",', 1)[1].split("));", 1)[0]
assert set(hidden) == set(re.findall(r'"([\w.]+)"', fallback)), "Hide-list recovery copy differs"
assert "com.onemainstream.sonyliv.android" in hidden
assert "in.startv.hotstar" in package_list("own-task-apps.txt")
validate(package_list("own-task-apps.txt"))
rows = json.loads((assets / "rows.json").read_text())["rows"]
assert isinstance(rows, list) and 1 <= len(rows) <= 4
for row in rows:
    assert isinstance(row, dict) and set(row) == {"packages"}
    assert isinstance(row["packages"], list)
validate([pkg for row in rows for pkg in row["packages"]])
print("PASS: launcher package policy and configured rows")
