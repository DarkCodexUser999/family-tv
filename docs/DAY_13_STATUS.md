# Day 13 — status, readability and reproducible checks

Issue #18. Open the remote's **Connection status** button. The phone fetches one
pinned, authenticated `/v1/status` snapshot on entry, foreground resume or manual
refresh. It shows whether Family TV is foreground/busy, cached catalog revision,
supported editing/upload capabilities and artwork job state. No token, key,
package list, pairing identity or job receipt is returned by this snapshot.

The Java endpoint reads existing cached fields only, with no package queries,
provider HTTP, disk reads, polling or additional process. Optional capability/job
fields preserve old clients; newer phones conservatively mark capabilities
unavailable when an older launcher omits them. An unreachable server is reported
as unreachable, never falsely identified as definitely disabled.

The phone cancels work on background/disposal, ignores old replies after resume,
coalesces refresh clicks, clears stale ready state after errors/revocation and
explains disabled remote controls, including input awaiting confirmation. Body
text is larger and uses the theme's high-contrast foreground; the scrollable
layout is checked at 320×640 with 200% text scaling. A test originally looked for
an off-screen row before scrolling; the following CI increment corrected that
assertion. No published history was rewritten.

## Verification

- Analyzer clean; 12 targeted status, lifecycle, large-text and remote tests pass.
- Cached snapshot tests cover revision/capabilities, foreground/busy/job state and
  absence of identifiers; real TLS checks cover authenticated status and bounds.
- Full host gate ran once locally; protocol validation is included in acceptance.
- CI pins Flutter revision `ab2912035e160d9cad6306df307576da6128ebdb`, runs the
  explicit mobile/status tests and Java interop, builds Android APKs, and adds a
  macOS 15 iOS simulator debug build without signing. Jobs fail on tool/build
  errors rather than silently treating an unavailable build as a pass.
- Initial [CI run](https://github.com/DarkCodexUser999/family-tv/actions/runs/45946031929)
  passed Java/native Android checks but exposed missing CocoaPods base-config
  includes in the first iOS build. The final increment restores the standard
  optional Pods includes before Generated.xcconfig and guards their order.
  Final build results and links are recorded on the
  [Day 13 project card](https://github.com/users/DarkCodexUser999/projects/1)
  after publication; consult the actual run rather than assuming a build passed.

## Acceptance still pending

No Fire TV or physical phone installation/test occurred. Same-build server OFF
versus ON PSS and focus/launch latency cannot be measured without the stick;
these are pending, not zero overhead claims. Simulator compilation does not prove
physical iOS discovery, permission, keychain or pairing behavior.

CI references: [GitHub runner labels](https://docs.github.com/en/actions/reference/runners/github-hosted-runners),
[Flutter iOS requirements](https://docs.flutter.dev/deployment/ios).
No local APK build or new dependency; launch/focus, hidden defaults, app data and
restore behavior are unchanged. Day 14 release/handoff needs a separate go.
