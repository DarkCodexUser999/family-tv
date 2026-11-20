"""Scan tracked paths without emitting matching lines or secret values."""
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
key_field = rb'(?:tmdb(?:[_-]?(?:api|read|access))?[_-]?(?:key|token)|api_key|authorization)'
literal = re.compile(key_field + rb'''["']?\s*[:=]\s*["']?(?:Bearer\s+)?(?:[a-f0-9]{32}\b|eyJ[A-Za-z0-9_.-]{40,})''', re.I)
jwt = re.compile(rb'eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{15,}')
def flagged(name, data):
    return (Path(name).name in {"tmdb_key.txt", "tmdb.properties"}
            or bool(literal.search(data) or jwt.search(data)))

assert flagged("synthetic.java", b'TMDB_API_KEY="' + b"ab" * 16 + b'"')
assert flagged("assets/tmdb_key.txt", b"synthetic")
assert not flagged("TmdbShelf.java", b'&api_key="+key')
paths = subprocess.check_output(["git","ls-files","-z"], cwd=root).split(b"\0")
for path in filter(None,paths):
    name = path.decode()
    file = root / name
    if file.is_file() and flagged(name,file.read_bytes()):
        raise SystemExit("Provider-secret check failed for " + name)
print("PASS: tracked provider-secret scan and synthetic positive/negative fixtures")
