# Security notes

This is an experimental local-network application, not a hardened remote administration product. The companion protocol authenticates requests but does not encrypt traffic. Use a trusted LAN and do not expose TCP 48321 or ADB to the internet. Pairing keys live in app-private preferences; do not share device backups or keys.

Do not post secrets or exploitable personal device details in public issues. Contact the repository owner privately through an available GitHub security/contact channel before sharing a sensitive report. No response-time or security-support guarantee is offered. Disabling ADB after development does not disable the companion protocol, which has a separate lifecycle.
