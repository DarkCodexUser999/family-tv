# Configuration

Asset edits require rebuilding and reinstalling the launcher. They are not live settings.

## Rows

`launcher/src/main/assets/rows.json` defines up to four ordered rows, each containing an ordered `packages` array. Package identifiers, not display names, are used. Example:

```json
{"rows":[
  {"packages":["com.netflix.ninja","com.amazon.firebat","com.amazon.firetv.youtube","in.startv.hotstar","com.jio.media.stb.ondemand"]},
  {"packages":["com.graymatrix.did","com.zee5.aTV","com.discovery.discoveryplus.firetv","com.discovery.discoveryplus.androidtv"]},
  {"packages":["com.amazon.venezia","com.android.providers.downloads.ui","com.amazon.cloud9","com.amazon.ftv.screensaver","com.amazon.ssm"]}
]}
```

Only installed, launchable, non-hidden packages are displayed. Empty rows are omitted. Unlisted apps enter the last row; unlisted apps whose display label contains ZEE5/discovery are routed to the second configured row. Generic video-app classification is not implemented. Package identifiers can vary between distributions: inspect the installed package before adding it. About six tiles per row is a design target, not a hard limit.

Rows after the first create their full interactive controls on first navigation. Before that, a lightweight, non-focusable preview draws the existing tile bitmaps at half opacity. About the top quarter of the next row is visible below the focused row. Earlier rows fade away to keep the upper screen for artwork. Once a row is built, its real controls supply the preview instead, avoiding duplicate artwork. TV visual/performance verification of this update is pending.

## Hidden tiles

`launcher/src/main/assets/hidden-apps.txt` has one package per line; `#` starts a comment. Removing a package from this file unhides it after rebuild/reinstall and restart. Sony LIV is deliberately hidden. CI checks that the bundled recovery list in AppCatalog matches this asset. Update both when editing defaults. Hiding does not uninstall, disable or clear packages.

## Per-app task flags

`launcher/src/main/assets/own-task-apps.txt` lists packages receiving `NEW_TASK | RESET_TASK_IF_NEEDED`. Other app launches retain the shared-task strategy. Changing task flags can affect BACK behavior and app initialization. Preserve the existing list unless testing a specific app failure.

## Artwork overrides

- Tile: `/sdcard/FamilyTV/tiles/<package>.png`
- Backdrop: `/sdcard/FamilyTV/backdrops/<package>.jpg`, or `.png` when no JPG exists

Create the directories if needed and transfer an image with ADB:

```sh
adb -s "$TV_IP:5555" shell mkdir -p /sdcard/FamilyTV/tiles /sdcard/FamilyTV/backdrops
adb -s "$TV_IP:5555" push my-tile.png /sdcard/FamilyTV/tiles/com.amazon.firetv.youtube.png
```

Use artwork you have permission to use. Tiles center-crop overrides; otherwise they use the app's TV banner, or a colored icon fallback. Tiles are pre-rendered into rounded ARGB_8888 bitmaps on the artwork worker. Backdrops use local overrides or generated gradient/icon artwork, decoded to approximately 640×240 RGB_565 and held in a four-entry cache. Restart the launcher after changing artwork. Override directories are read-only inputs for the launcher. Generated artwork is cached privately and reused when the sampled icon/color fingerprint matches; overrides still take precedence.

## Display sizing

The tested output is 1920×1080 at 320 dpi, which is distinct from a television's physical panel resolution. Current tiles are about 19% of output width, 16:9, with about 3% horizontal gaps and 5% side margins. Four whole tiles fit at this configuration; 4.5 visible tiles is not achieved. Vertical positions currently scale from width using the tested 16:9 output; unusual aspect ratios are not verified.

## Remembered row focus

Moving DOWN or UP returns to the last selected tile in that row for the current launcher session. The first visit uses the source row's selected column, clamped for a shorter destination row. Focus is remembered as an integer per row without disk writes on direction presses.

The existing last-app preference also restores an app in a lazy lower row after launcher recreation: Family TV locates the saved key and creates that row before requesting focus. If the app was removed, hidden or no longer appears in the catalog, focus falls back to the first available Favorite as before. Per-row history resets on recreation; the single last-app key remains persisted. Device verification is pending.

The gear and MENU open Family TV options. Choose **Fire TV Settings** to leave the launcher for system settings. If installed-app discovery fails, the same menu offers **Retry loading apps**.
