# Contributing

Read README.md, docs/SETUP.md and docs/TESTING.md before proposing changes. The project has not selected a redistribution license; discuss contributions with the maintainer first.

Keep Java/API 22 compatibility, minimal dependencies and bounded memory usage. Do not disable system packages, clear third-party app data, add root requirements or silently change per-app task flags. Keep configuration separate from rendering. Use local artwork and pre-render expensive graphics away from the UI thread.

For a bug report include model, Fire OS/API level, app versions, expected behavior, actual behavior and concise reproduction steps. Redact serials, account names, local addresses, pairing codes and keys from logs. For a patch describe affected behavior, build result, device validation, and known gaps. Do not claim a performance improvement without comparable evidence.

Before submitting a change, run `sh tests/check-all.sh` with JDK 17 configured, then build both modules. GitHub repeats these checks on pushes and pull requests. See docs/CI.md for scope and setup. A green build does not replace Fire TV testing.
