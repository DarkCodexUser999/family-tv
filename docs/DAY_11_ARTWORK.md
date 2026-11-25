# Day 11 — artwork upload

Open Remote → Apps → TV artwork. Choose an app, tile/backdrop and PNG/JPEG, adjust the horizontal/vertical crop, then Upload. Progress shows bytes sent; Cancel is available before final confirmation. Reset removes only the uploaded image and restores existing TV-local/default artwork. Existing TV-local files retain priority.

The system document picker requires no broad photo/storage permission. The preview is decoded to at most 512 pixels per side. No runtime dependency was added.

All artwork endpoints require existing pinned TLS/bearer authentication and a cached installed package target. Raw uploads are capped at 2 MiB, PNG/JPEG signatures, PNG chunk CRC/end checks, JPEG frame/end checks and 8 MP. Android additionally checks decoder bounds and samples to at most 1280 pixels per side before crop/re-encoding. Only one upload job runs; normalized images remain capped at 2 MiB with a 32 MiB private artwork quota and an 8 MiB free-space check.

Staging writes a private temporary file and never changes the visible image. A phone-owned 128-bit receipt expires after 60 seconds; a separate authenticated commit atomically renames the normalized file. Interrupted/corrupt/unauthorized/quota failures retain previous artwork. Cancellation before commit never publishes. A connection lost during final confirmation is uncertain: check the TV before retrying, with no automatic replay. Bounded abandoned staging is cleaned on the next upload; no polling timer is added.

Only the affected package cache entries are invalidated. Existing artwork workers refresh matching tile/shelf views without changing app launching, focus or navigation code. Reset deletes only the private uploaded override.

Checks: upload/failure/quota tests, artwork-only real TLS auth/stage/commit/cancel/reset tests, four picker/crop/progress/reset widgets, protocol validation, Android artwork Java source compilation; the full host gate ran once before the last commit. No screenshots, APK builds or installs were performed. Native picker/decoder execution and physical memory/navigation acceptance remain pending.
