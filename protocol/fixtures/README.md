# Protocol examples

`messages.json` contains synthetic data only. Each entry names a schema, an input value and the expected validity. `event` selects the WebSocket envelope; other names select a shared definition in events.schema.json.

From the repository, use the SSD-hosted validator environment:

```sh
../tools/protocol-venv/bin/python tests/check-protocol.py
```

Expected: 8 positive and 19 negative fixtures pass. Review `action request` versus `forbidden action SHELL` to see the capability boundary. The script also checks authentication defaults, If-Match presence, cross-row uniqueness and envelope ID consistency. It is not a network simulator or proof of server authorization.

`openapi.yaml` uses JSON syntax (valid YAML) and resolves its schemas from the adjacent events.schema.json. Dependencies are pinned in requirements-dev.txt. No phone UI or TV server is available yet; this is the Day 1 Mac-side demo.
