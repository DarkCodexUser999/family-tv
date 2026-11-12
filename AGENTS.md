# Family TV launcher

- Keep the launcher compatible with the second-generation Fire TV Stick (Fire OS 5, Android API 22, 1 GB RAM). Use native Java views and avoid new runtime dependencies for visual effects.
- Design for a couch viewing distance. Use roughly 5% safe-area margins, readable labels, and large tiles. Fit the number and size of cards to the available TV viewport.
- Make D-pad focus visible with a distinct outline and subtle lift, scale, and tilt. Set initial focus explicitly and restore the same item when returning, with a sensible fallback.
- Use pre-rendered ambient bitmaps and inexpensive view transforms. Keep motion brief and verify fast D-pad navigation and edge clipping on the actual stick.
- Treat reference repositories as design references. Preserve phone pairing, physical remote navigation, Amazon Home, and the ability to uninstall only Family TV.
