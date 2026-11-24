package app.firetvremote.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LauncherActivity extends Activity {
    private static final int PRIMARY_TEXT = 0xfff4f7ff;
    private static final String LOG_TAG = "FamilyTV";
    private final Map<String, Button> focusTargets = new LinkedHashMap<>();
    private static final String SETTINGS_KEY = "settings";
    private Button gear;
    private boolean catalogFailed, catalogDirty;
    private long loadedRowsRevision=-1,loadedHiddenRevision=-1;
    private final android.content.BroadcastReceiver packageChanges=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context context,Intent intent){
            catalogDirty=true;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.apps=null;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.management=null;
            if(!isSettingsLaunchPaused && !isFinishing())recreate();
        }
    };
    private boolean isSettingsLaunchPaused;
    private boolean settingsOpening;
    private Button firstFavorite;
    private FocusTrace trace;
    private TopShelf backdrop;
    private ArtworkLoader artwork;
    private FrameLayout scene;
    private FrameLayout rows;
    private FrameLayout[] trays;
    private int wantedRow = -1, wantedColumn;
    private boolean preparationPosted;
    private String confirmKey;
    private boolean confirmReleased;
    private boolean animateArrival;
    private final android.os.MessageQueue.IdleHandler idle = () -> {
        preparationPosted = false;
        if (!isSettingsLaunchPaused && !isDestroyed() && prepareOne()) schedulePreparation();
        return false;
    };
    private final Runnable armIdle = () -> android.os.Looper.myQueue().addIdleHandler(idle);

    private float uiScale;
    private volatile int activeRow;
    private final Runnable refreshTiles = this::refreshVisibleTiles;
    private RowFocusMemory rowFocus;
    private boolean fastNavigation;
    private long lastNavigationDown = -1;
    private View[] rowPreviews;
    private final List<List<TileData>> rowData = new ArrayList<>();

    private static final class TileData {
        final String key, label, pkg;
        final Runnable launch;
        volatile Button button;

        TileData(CatalogEntry entry, Runnable launch) {
            pkg = entry.route.pkg;
            key = "app:" + pkg;
            label = entry.label;
            this.launch = launch;
        }
    }
    private String focusedKey;
    private String focusOnReturn;
    private String lastSavedFocus;
    private Set<String> hiddenPackages;
    private Set<String> ownTaskPackages;
    private Button pendingTile;
    private CharSequence pendingTileLabel;
    private boolean launchDispatched;
    private final Runnable releasePending = () -> {
        if (pendingTile != null) {
            if (pendingTileLabel != null) pendingTile.setText(pendingTileLabel);
            pendingTile.setAlpha(1f);
            pendingTile = null;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.busy(this.lanUi,false);
            pendingTileLabel = null;
        }
        launchDispatched = false;
        applyRowsIfReady();
    };
    private AlertDialog remoteDialog;
    private final android.os.Handler pairUi=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pairExpiry=() -> { if(remoteDialog!=null) remoteDialog.dismiss(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app.firetvremote.launcher.lan.LauncherBridge.SHARED.apps=null;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.management=null;
        android.content.IntentFilter changes=new android.content.IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED);changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED);changes.addDataScheme("package");
        registerReceiver(packageChanges,changes);
        hiddenPackages = AppCatalog.hiddenPackages(this);
        ownTaskPackages = AppCatalog.readPackages(this, "own-task-apps.txt");

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        uiScale = metrics.widthPixels / 1920f;
        scene = new FrameLayout(this);
        scene.setBackgroundColor(0xff050609);
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            trace = new FocusTrace();
            animateArrival = getIntent().getBooleanExtra("short-focus-transform", false);
        }
        artwork = new ArtworkLoader(this);
        backdrop = new TopShelf(this, artwork);
        backdrop.trace = trace;
        scene.addView(backdrop, new FrameLayout.LayoutParams(-1, px(760)));

        setContentView(scene);
        final String restore = savedInstanceState == null
                ? getPreferences(MODE_PRIVATE).getString("last-focus", null)
                : savedInstanceState.getString("last-focus");
        lastSavedFocus = restore;
        artwork.submit(() -> loadCatalog(restore));
    }

    private void loadCatalog(String restore) {
        Map<String, CatalogEntry> installed;
        try {
            installed = AppCatalog.list(this, java.util.Collections.emptySet(), ownTaskPackages, artwork);
        } catch (RuntimeException error) {
            Log.w(LOG_TAG, "App discovery unavailable", error);
            runOnUiThread(() -> {
                if (catalogDirty || isDestroyed() || isFinishing()) return;
                catalogFailed = true;
                buildHome();
            });
            return;
        }

        final app.firetvremote.launcher.lan.HiddenEndpoint exclusions=RuntimeConfigStore.hidden(this,hiddenPackages);
        final long hiddenRevision=exclusions.snapshot().revision;
        final Map<String,CatalogEntry> managementInstalled=new LinkedHashMap<>(installed);
        installed.keySet().removeAll(exclusions.snapshot().packages);
        final app.firetvremote.launcher.lan.RowsEndpoint config=RuntimeConfigStore.open(this,RowConfig.read(this),installed.keySet());
        final app.firetvremote.launcher.lan.RowsEndpoint.State rowSettings=config.snapshot();
        List<List<TileData>> prepared = new ArrayList<>();
        for (List<CatalogEntry> row : CatalogRows.arrange(installed, rowSettings.rows)) {
            List<TileData> tiles = new ArrayList<>();
            for (CatalogEntry entry : row) {
                if (artwork.cancelled()) return;
                Intent intent = AppCatalog.intent(entry.route);
                tiles.add(new TileData(entry, () -> openIntent(entry.label, intent)));
                Log.i(LOG_TAG, "RowTile " + entry.route.pkg);
            }
            prepared.add(tiles);
        }
        runOnUiThread(() -> {
            if (catalogDirty || isDestroyed() || isFinishing()) return;
            if(config.snapshot().revision!=rowSettings.revision || exclusions.snapshot().revision!=hiddenRevision){recreate();return;}
            loadedRowsRevision=rowSettings.revision;loadedHiddenRevision=hiddenRevision;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.hidden=exclusions;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.rows=config;
            rowData.addAll(prepared);
            buildHome();
            restoreFocus(restore);
            if(app.firetvremote.launcher.lan.LanService.enabled(this))
                artwork.submitCatalog(() -> preparePhoneCatalog(managementInstalled));
        });
    }

    private void preparePhoneCatalog(Map<String,CatalogEntry> installed) {
        List<app.firetvremote.launcher.lan.AppsEndpoint.App> phoneApps = new ArrayList<>();
        for (CatalogEntry entry : installed.values()) {
            if (artwork.cancelled()) return;
            if (phoneApps.size() >= app.firetvremote.launcher.lan.AppsEndpoint.MAX_APPS) break;
            Bitmap thumbnail = artwork.loadTile("phone:" + entry.route.pkg, entry.route.pkg, 96, 54);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            if (thumbnail != null) thumbnail.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            phoneApps.add(new app.firetvremote.launcher.lan.AppsEndpoint.App(entry.route.pkg, entry.label, bytes.toByteArray()));
        }
        final app.firetvremote.launcher.lan.AppsEndpoint phoneCatalog =
                new app.firetvremote.launcher.lan.AppsEndpoint(android.os.SystemClock.elapsedRealtime(), phoneApps);

        runOnUiThread(() -> {
            if(!catalogDirty && !isDestroyed() && !isFinishing()
                    && app.firetvremote.launcher.lan.LanService.enabled(this))
                {
                app.firetvremote.launcher.lan.LauncherBridge.SHARED.management=phoneCatalog;
                app.firetvremote.launcher.lan.LauncherBridge.SHARED.apps=phoneCatalog.visible(app.firetvremote.launcher.lan.LauncherBridge.SHARED.hidden.snapshot().packages);
            }
        });
    }

    private int px(int value) { return Math.round(value * uiScale); }

    private void buildHome() {
        rows = new FrameLayout(this);
        rows.setClipChildren(false); rows.setClipToPadding(false);
        FrameLayout viewport = new FrameLayout(this);
        scene.addView(viewport, new FrameLayout.LayoutParams(-1, px(1026)));
        viewport.addView(rows, new FrameLayout.LayoutParams(-1, px(2400)));
        String[][] keys = new String[rowData.size()][];
        for (int row = 0; row < rowData.size(); row++) {
            keys[row] = new String[rowData.get(row).size()];
            for (int col = 0; col < keys[row].length; col++) keys[row][col] = rowData.get(row).get(col).key;
        }
        rowFocus = new RowFocusMemory(keys);
        rowPreviews = new View[rowData.size()];
        trays = new FrameLayout[rowData.size()];
        for (int row = 0; row < trays.length; row++) {
            FrameLayout tray = new FrameLayout(this);
            trays[row] = tray;
            tray.setBackground(traySurface());
            tray.setAlpha(RowPresentation.alpha(row, 0));
            rows.addView(tray, rowPosition(row));
            if (row > 0) {
                rowPreviews[row] = createRowPreview(row);
                rows.addView(rowPreviews[row], rowPosition(row));
            }
        }
        if (trays.length > 0) {
            for (int col = 0; col < Math.min(5, rowData.get(0).size()); col++) attachTile(0, col);
            firstFavorite = rowData.get(0).get(0).button;
        }
        android.widget.TextClock clock = new android.widget.TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTextColor(PRIMARY_TEXT); clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(72));
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(330), px(96), Gravity.TOP | Gravity.RIGHT);
        cp.topMargin = px(54); cp.rightMargin = px(220); scene.addView(clock, cp);
        gear = new Button(this);
        gear.setId(View.generateViewId()); gear.setText("⚙"); gear.setContentDescription("Family TV settings");
        gear.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(64));
        gear.setTextColor(PRIMARY_TEXT); gear.setPadding(0,0,0,0); gear.setStateListAnimator(null);
        final Drawable settingsIdle = settingsSurface(false);
        final Drawable settingsFocused = settingsSurface(true);
        gear.setBackground(settingsIdle);
        gear.setOnFocusChangeListener((v, focused) -> {
            gear.setBackground(focused ? settingsFocused : settingsIdle);
            if (focused) {
                focusedKey = SETTINGS_KEY;
                app.firetvremote.launcher.lan.LauncherBridge.SHARED.focus(null);
            }
        });
        gear.setOnClickListener(v -> showOptions());
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(px(96), px(96), Gravity.TOP | Gravity.RIGHT);
        gp.topMargin = px(54); gp.rightMargin = px(96); scene.addView(gear, gp);
        if (firstFavorite != null) {
            schedulePreparation();
            gear.setNextFocusDownId(firstFavorite.getId());
        } else {
            TextView empty = new TextView(this);
            empty.setText(catalogFailed ? "Couldn’t load apps. Open Settings to retry."
                    : "No apps to show. Check installed apps in Settings.");
            empty.setTextColor(PRIMARY_TEXT);
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(40));
            empty.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(px(1728), px(200));
            ep.leftMargin = px(96); ep.topMargin = px(700);
            scene.addView(empty, ep);
            gear.requestFocus();
        }
        Log.i(LOG_TAG, "UI tile=" + px(365) + "x" + px(205) + " gap=" + px(58));
    }

    private void schedulePreparation() {
        if (preparationPosted || isSettingsLaunchPaused || isDestroyed()) return;
        preparationPosted = true;
        scene.postOnAnimation(armIdle);
    }

    private void attachTile(int row, int column) {
        TileData tile = rowData.get(row).get(column);
        tile.button = addShortcut(row, column, tile.key, tile.pkg, tile.label, tile.launch);
        if (visibleRow(row)) bindTileArt(row, tile);
        tile.button.setTag(column);
        positionTile(row, column, tile.button);
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(px(365), px(205));
        size.topMargin = px(32);
        trays[row].addView(tile.button, size);
    }

    private boolean visibleRow(int row) { return row == activeRow || row == activeRow + 1; }

    private void refreshVisibleTiles() {
        if (isFinishing() || isDestroyed() || trays == null) return;
        for (int row = 0; row < trays.length; row++) {
            for (int i = 0; i < trays[row].getChildCount(); i++) {
                TileButton button = (TileButton)trays[row].getChildAt(i);
                if (visibleRow(row)) bindTileArt(row, rowData.get(row).get((Integer)button.getTag()));
                else if (button.hasArt()) button.clearArt();
            }
        }
    }

    private void bindTileArt(int row, TileData tile) {
        final TileButton target = (TileButton)tile.button;
        if (target.hasArt() || target.requestingArt) return;
        Bitmap hit = artwork.cached(tile.key);
        if (hit != null) target.setArt(hit);
        else {
            target.requestingArt = true;
            final int w=px(365), h=px(205);
            artwork.submit(() -> {
                if (artwork.cancelled() || tile.button != target) return;
                Bitmap art = null;
                try {
                    art = artwork.loadTile(tile.key, tile.pkg, w, h);
                } catch (RuntimeException unavailable) { /* Keep the tile launchable. */ }
                if (artwork.cancelled()) return;
                final Bitmap result = art;
                int color = result == null ? 0xff374157 : ArtworkLoader.dominant(result);
                runOnUiThread(() -> {
                    if (catalogDirty || isDestroyed() || isFinishing()) return;
                    target.requestingArt = false;
                    backdrop.tint(tile.pkg,color);
                    if (result != null && tile.button == target && visibleRow(row)) target.setArt(result);
                    if (rowPreviews[row]!=null) rowPreviews[row].invalidate();
                });
            });
        }
    }

    private void positionTile(int row, int column, Button button) {
        int selected = rowFocus.selection(row, 0);
        int count = rowData.get(row).size();
        int offset = Math.min(Math.max(0, count * 423 + 30 - 1728), Math.max(0, (selected - 1) * 423));
        float x = px(34 + column * 423 - offset);
        if (button.getTranslationX() != x) button.setTranslationX(x);
    }

    // One new Button per idle slice. Keep only the viewport plus adjacent controls.
    private boolean prepareOne() {
        if (trays == null || trays.length == 0) return false;
        if (wantedRow >= 0) {
            int row = wantedRow, col = wantedColumn;
            TileData tile = rowData.get(row).get(col);
            int start = windowStart(col, rowData.get(row).size());
            pruneRow(row, start, start + 7);
            if (tile.button == null) attachTile(row, col);
            wantedRow = -1;
            retirePreview(row);
            tile.button.requestFocus();
            if (confirmReleased && tile.key.equals(confirmKey) && tile.button.isFocused()) {
                confirmKey = null;
                tile.button.performClick();
            }
            return true;
        }
        for (int pass = 0; pass < trays.length; pass++) {
            int row = (activeRow + pass) % trays.length;
            List<TileData> data = rowData.get(row);
            int col = rowFocus.selection(row, 0);
            int start = windowStart(col, data.size());
            int end = Math.min(data.size(), start + 7);
            pruneRow(row, start, end);
            for (int i = start; i < end; i++) {
                if (data.get(i).button == null) {
                    attachTile(row, i);
                    return true;
                }
            }
            retirePreview(row);
        }
        return false;
    }

    private static int windowStart(int column, int size) {
        return Math.max(0, Math.min(column - 2, size - 7));
    }

    private void pruneRow(int row, int start, int end) {
        FrameLayout tray = trays[row];
        List<TileData> data = rowData.get(row);
        for (int i = tray.getChildCount() - 1; i >= 0; i--) {
            Button button = (Button)tray.getChildAt(i);
            int index = (Integer)button.getTag();
            if ((index < start || index >= end) && !button.isFocused()
                    && button != firstFavorite && button != pendingTile) {
                tray.removeViewAt(i);
                TileData tile = data.get(index);
                tile.button = null;
                focusTargets.remove(tile.key);
            }
        }
    }

    private void retirePreview(int row) {
        if (rowPreviews[row] != null) {
            rows.removeView(rowPreviews[row]);
            rowPreviews[row] = null;
        }
    }

    private void select(int row, int col) {
        Button target = rowData.get(row).get(col).button;
        wantedRow = -1;
        if (target != null) target.requestFocus();
        else {
            wantedRow = row;
            wantedColumn = col;
        }
        schedulePreparation();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (wantedRow >= 0 || confirmKey != null) {
                if (down && event.getRepeatCount() == 0 && wantedRow >= 0) {
                    confirmKey = rowData.get(wantedRow).get(wantedColumn).key;
                    confirmReleased = false;
                } else if (!down && confirmKey != null) {
                    confirmReleased = !event.isCanceled();
                    if (wantedRow < 0 && confirmReleased && confirmKey.equals(focusedKey))
                        getCurrentFocus().performClick();
                    if (wantedRow < 0 || !confirmReleased) confirmKey = null;
                }
                return true;
            }
        }
        if (gear != null && gear.isFocused() && down && key == KeyEvent.KEYCODE_DPAD_DOWN
                && firstFavorite != null) {
            focusFirstFavorite();
            return true;
        }
        if (!down || rowFocus == null || !(getCurrentFocus() instanceof TileButton))
            return super.dispatchKeyEvent(event);
        int row = wantedRow >= 0 ? wantedRow : activeRow;
        int col = wantedRow >= 0 ? wantedColumn : rowFocus.selection(row, 0);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: col = Math.max(0, col - 1); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: col = Math.min(rowData.get(row).size() - 1, col + 1); break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row == 0) {
                    wantedRow = -1;
                    confirmKey = null;
                    gear.requestFocus();
                    return true;
                }
                col = rowFocus.selection(--row, col); break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row + 1 >= trays.length) return true;
                col = rowFocus.selection(++row, col); break;
            default: return super.dispatchKeyEvent(event);
        }
        confirmKey = null;
        if (trace != null) trace.down(event);
        fastNavigation = NavigationTiming.isRapid(lastNavigationDown, event.getEventTime(), event.getRepeatCount());
        lastNavigationDown = event.getEventTime();
        rowFocus.remember(row, col);
        select(row, col);
        return true;
    }

    private FrameLayout.LayoutParams rowPosition(int row) {
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(px(1728), px(285));
        position.leftMargin = px(96);
        position.topMargin = px(RowPresentation.top(row));
        return position;
    }

    /** Non-interactive peek shares existing tile bitmaps; full controls remain lazy. */
    private View createRowPreview(int row) {
        List<TileData> tiles = rowData.get(row);
        final int count = Math.min(5, tiles.size());

        final android.graphics.Rect[] destinations = new android.graphics.Rect[count];
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < count; i++) {

            int left = px(34 + i * 423);
            destinations[i] = new android.graphics.Rect(left, px(32), left + px(365), px(237));
        }
        View preview = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                for (int i = 0; i < count; i++) {
                    Bitmap art = artwork.cached(tiles.get(i).key);
                    if (art != null) canvas.drawBitmap(art,null,destinations[i],paint);
                }
            }
        };
        preview.setFocusable(false);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setAlpha(RowPresentation.alpha(row, activeRow));
        return preview;
    }

    private Drawable settingsSurface(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xff202631);
        shape.setCornerRadius(px(30));
        shape.setStroke(px(focused ? 4 : 1), focused ? 0xffffffff : 0xff657080);
        return shape;
    }

    private Drawable traySurface() {
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff202631, 0xff111620});
        panel.setCornerRadius(px(62)); panel.setStroke(px(1), 0x66ffffff); return panel;
    }

    private void focusScene(int row, int column, String pkg) {
        rowFocus.remember(row, column);
        if (rowPreviews[row] != null) rowPreviews[row].setVisibility(View.INVISIBLE);
        FrameLayout tray = trays[row];
        for (int i = 0; i < tray.getChildCount(); i++) {
            Button tile = (Button)tray.getChildAt(i);
            positionTile(row, (Integer)tile.getTag(), tile);
        }
        if (activeRow != row) {
            activeRow = row;
            scene.removeCallbacks(refreshTiles);
            scene.postOnAnimation(refreshTiles);
            rows.setTranslationY(px(RowPresentation.translation(row)));
            for (int i = 0; i < trays.length; i++) {
                float alpha = RowPresentation.alpha(i, row);
                if (trays[i].getAlpha() != alpha) trays[i].setAlpha(alpha);
                View preview = rowPreviews[i];
                if (preview != null && preview.getAlpha() != alpha) preview.setAlpha(alpha);
            }
        }
        backdrop.show(pkg, fastNavigation);
        schedulePreparation();
    }

    private void focusFirstFavorite() {
        wantedRow = -1;
        confirmKey = null;
        if (firstFavorite != null) {
            rowFocus.remember(0, 0);
            firstFavorite.requestFocus();
        }
    }

    private void restoreFocus(String key) {
        Button target = SETTINGS_KEY.equals(key) ? gear : focusTargets.get(key);
        // The saved app may live in a row whose interactive Views are still lazy.
        if (target == null && rowFocus != null) {
            int row = rowFocus.rowOf(key);
            if (row >= 0) {
                List<TileData> data = rowData.get(row);
                for (int col = 0; col < data.size(); col++) {
                    if (key.equals(data.get(col).key)) {
                        select(row, col);
                        return;
                    }
                }
            }
        }
        if (target == null || !target.requestFocus()) focusFirstFavorite();
    }

    @Override
    protected void onPause() {
        isSettingsLaunchPaused = true;
        wantedRow = -1;
        confirmKey = null;
        scene.removeCallbacks(armIdle);
        android.os.Looper.myQueue().removeIdleHandler(idle);
        preparationPosted = false;
        settingsOpening = false;
        scene.removeCallbacks(launchSettings);
        app.firetvremote.launcher.lan.LauncherBridge.SHARED.pause(lanUi);
        focusOnReturn = focusedKey;
        if (!TextUtils.equals(lastSavedFocus, focusedKey)) {
            getPreferences(MODE_PRIVATE).edit().putString("last-focus", focusedKey).apply();
            lastSavedFocus = focusedKey;
        }
        backdrop.suspend();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(catalogDirty || rowsChanged()){recreate();return;}
        isSettingsLaunchPaused = false;
        backdrop.resume();
        schedulePreparation();
        if (launchDispatched) clearPending();
        if (focusOnReturn != null && getCurrentFocus() != focusTargets.get(focusOnReturn)) {
            restoreFocus(focusOnReturn);
        }
        focusOnReturn = null;
        app.firetvremote.launcher.lan.LauncherBridge.SHARED.resume(lanUi);
        app.firetvremote.launcher.lan.LauncherBridge.SHARED.busy(lanUi,pendingTile!=null);
    }

    @Override
    public void onBackPressed() {
        if (remoteDialog != null) remoteDialog.dismiss();
        else focusFirstFavorite();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("last-focus", focusedKey);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        app.firetvremote.launcher.lan.LanService.notice=pairNotice;
        app.firetvremote.launcher.lan.LanService.resumeIfEnabled(this);
    }

    @Override
    protected void onStop() {
        if(app.firetvremote.launcher.lan.LanService.notice==pairNotice) app.firetvremote.launcher.lan.LanService.notice=null;
        if (remoteDialog != null) remoteDialog.dismiss();
        super.onStop();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (artwork != null && level >= TRIM_MEMORY_RUNNING_LOW) artwork.trim();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageChanges);
        pairUi.removeCallbacks(pairExpiry);
        settingsOpening = false;
        scene.removeCallbacks(launchSettings);
        clearPending();
        scene.removeCallbacks(refreshTiles);
        if (backdrop != null) backdrop.close();
        if (artwork != null) artwork.close();
        super.onDestroy();
    }

    private void clearPending() {
        if (pendingTile != null) pendingTile.removeCallbacks(releasePending);
        releasePending.run();
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU) {
            showOptions();
            return true;
        }
        return super.onKeyUp(code, event);
    }

    private boolean rowsChanged() {
        app.firetvremote.launcher.lan.RowsEndpoint config=app.firetvremote.launcher.lan.LauncherBridge.SHARED.rows;
        app.firetvremote.launcher.lan.HiddenEndpoint hidden=app.firetvremote.launcher.lan.LauncherBridge.SHARED.hidden;
        return loadedRowsRevision>=0 && config!=null && (config.snapshot().revision!=loadedRowsRevision || hidden!=null&&hidden.snapshot().revision!=loadedHiddenRevision);
    }
    private void applyRowsIfReady() {
        if(rowsChanged() && !isSettingsLaunchPaused && pendingTile==null && !settingsOpening
                && remoteDialog==null && !isFinishing() && !isDestroyed())recreate();
    }

    private final app.firetvremote.launcher.lan.LauncherBridge.Ui lanUi=new app.firetvremote.launcher.lan.LauncherBridge.Ui(){
        @Override public void post(Runnable task){pairUi.post(task);}
        @Override public void rowsChanged(){applyRowsIfReady();}
        @Override public boolean launch(String pkg){
            if(isFinishing() || isSettingsLaunchPaused || pendingTile!=null || remoteDialog!=null || settingsOpening)return false;
            for(int row=0;row<rowData.size();row++)for(int col=0;col<rowData.get(row).size();col++){
                TileData tile=rowData.get(row).get(col);
                if(!tile.pkg.equals(pkg))continue;
                if(tile.button==null)attachTile(row,col);
                select(row,col);
                // Reuse the existing feedback and launch callback; artwork remains asynchronous.
                return tile.button!=null && tile.button.isFocused() && tile.button.performClick();
            }return false;
        }
        @Override public boolean perform(String action){
            if(!app.firetvremote.launcher.lan.ActionMessage.allowed(action)||isFinishing())return false;
            handleFamilyAction(action);return true;
        }
    };
    private final app.firetvremote.launcher.lan.LanService.Notice pairNotice=message -> {
        if(remoteDialog!=null) remoteDialog.dismiss();
        showRemoteDialog(new AlertDialog.Builder(this).setTitle("Phone access")
                .setMessage(message).setPositiveButton("Close",null));
        if(message.startsWith("PIN:")) pairUi.postDelayed(pairExpiry,120000); // One-shot expiry, no polling.
    };
    private void showOptions() {
        if (remoteDialog != null) return;
        wantedRow = -1;
        confirmKey = null;
        boolean enabled = app.firetvremote.launcher.lan.LanService.enabled(this);
        List<String> options = new ArrayList<>(java.util.Arrays.asList("Remote access: " + (enabled ? "ON" : "OFF"),
                        "Remote connection details", "Pair a phone", "Revoke all phone access", "Forget legacy phones",
                        "Fire TV Settings"));
        if (catalogFailed) options.add("Retry loading apps");
        showRemoteDialog(new AlertDialog.Builder(this).setTitle("Family TV settings")
                .setItems(options.toArray(new String[0]), (dialog, choice) -> {
                    if (choice == 6) { recreate(); return; }
                    if (choice == 5) { openFireSettings(); return; }
                    if (choice == 0) {
                        app.firetvremote.launcher.lan.LanService.setEnabled(this, !enabled);
                        app.firetvremote.launcher.lan.LauncherBridge.SHARED.apps = null;
                        recreate(); // Rebuild the phone snapshot only when explicitly enabled.
                    }
                    else if (choice == 1) showRemoteDialog(new AlertDialog.Builder(this)
                            .setTitle("Remote access").setMessage(app.firetvremote.launcher.lan.LanService.connectionDetails(this))
                            .setPositiveButton("Close", null));
                    else if(choice==2 || choice==3) {
                        if(choice==2 && !enabled) showRemoteDialog(new AlertDialog.Builder(this).setTitle("Remote access is OFF")
                                .setMessage("Enable remote access first.").setPositiveButton("Close",null));
                        else if(choice==2) app.firetvremote.launcher.lan.LanService.action(this,"pair");
                        else showRemoteDialog(new AlertDialog.Builder(this).setTitle("Revoke all phones?")
                                .setNegativeButton("Cancel",null).setPositiveButton("Revoke",(confirm,which) ->
                                        app.firetvremote.launcher.lan.LanService.action(this,"revoke")));
                    }
                    else showRemoteDialog(new AlertDialog.Builder(this)
                            .setTitle("Forget legacy paired phones?").setNegativeButton("Cancel", null)
                            .setPositiveButton("Forget", (confirm, which) ->
                                    getSharedPreferences("paired-phones", MODE_PRIVATE).edit().clear().apply()));
                }).setNegativeButton("Close", null));
    }

    private void showRemoteDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        remoteDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            pairUi.removeCallbacks(pairExpiry);
            if (remoteDialog == dialog) remoteDialog = null;
            applyRowsIfReady();
        });
        dialog.show();
    }

    private void handleFamilyAction(String action) {
        if ("HOME".equals(action)) {
            if (remoteDialog != null) remoteDialog.dismiss();
            focusFirstFavorite();
            return;
        }
        if ("OPTIONS".equals(action)) {
            showOptions();
            return;
        }
        int key;
        switch (action) {
            case "UP": key = KeyEvent.KEYCODE_DPAD_UP; break;
            case "DOWN": key = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "LEFT": key = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "RIGHT": key = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "OK": key = KeyEvent.KEYCODE_DPAD_CENTER; break;
            case "BACK":
                if (remoteDialog != null) remoteDialog.dismiss();
                else focusFirstFavorite();
                return;
            default: return;
        }
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, key);
        KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, key);
        if (remoteDialog != null) {
            remoteDialog.dispatchKeyEvent(down);
            if (remoteDialog != null) remoteDialog.dispatchKeyEvent(up);
        } else {
            dispatchKeyEvent(down);
            dispatchKeyEvent(up);
        }
    }

    private Button addShortcut(int row, int column, String key, String artworkPackage, String label, Runnable onSelect) {
        Button button = new TileButton(this, px(40), px(4));
        button.setId(View.generateViewId());
        button.setAllCaps(false);
        button.setText("");
        button.setContentDescription(label);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(40));
        button.setMaxLines(1);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
        final Button tileButton = button;
        ((TileButton)button).trace = trace;
        ((TileButton)button).pkg = artworkPackage;
        button.setBackground(null);
        button.setTextColor(PRIMARY_TEXT);
        button.setFocusable(true);
        button.setAlpha(.85f);
        button.setElevation(0);
        tileButton.setOnFocusChangeListener((view, focused) -> {
            float alpha = focused ? 1f : .85f;
            if (tileButton.getAlpha() != alpha) tileButton.setAlpha(alpha);
            float scale = focused ? 1.05f : 1f;
            if (animateArrival) tileButton.animate().cancel();
            if (focused && animateArrival && !fastNavigation) {
                tileButton.animate().scaleX(scale).scaleY(scale).setDuration(60).start();
            } else {
                if (tileButton.getScaleX() != scale) tileButton.setScaleX(scale);
                if (tileButton.getScaleY() != scale) tileButton.setScaleY(scale);
            }
            if (focused) {
                if (trace != null) trace.select(artworkPackage);
                focusedKey = key;
                app.firetvremote.launcher.lan.LauncherBridge.SHARED.focus(artworkPackage);
                focusScene(row, column, artworkPackage);
            }
        });
        // Allow one frame for pressed/loading feedback, then dispatch after traversal.
        final Runnable dispatchLaunch = () -> {
            if (pendingTile != tileButton || isFinishing()) return;
            launchDispatched = true;
            onSelect.run();
        };
        final Runnable afterFeedback = () -> tileButton.post(dispatchLaunch);
        tileButton.setOnClickListener(view -> {
            if ("options".equals(key)) {
                onSelect.run();
                return;
            }
            if (pendingTile != null) return;
            pendingTile = tileButton;
            app.firetvremote.launcher.lan.LauncherBridge.SHARED.busy(lanUi,true);
            if ("app:in.startv.hotstar".equals(key)) {
                pendingTileLabel = tileButton.getText();
                tileButton.setText("Loading…");
                tileButton.setAlpha(0.85f);
            } else {
                tileButton.setAlpha(0.55f);
            }
            tileButton.postDelayed(releasePending, 5000);
            tileButton.postOnAnimation(afterFeedback);
        });
        focusTargets.put(key, button);

        return button;
    }

    private void openFireSettings() {
        if (pendingTile != null || settingsOpening) return;
        if (remoteDialog != null) remoteDialog.dismiss();
        settingsOpening = true;
        Toast.makeText(this, "Opening Fire TV Settings…", Toast.LENGTH_SHORT).show();
        scene.postOnAnimation(() -> { if (settingsOpening) scene.post(launchSettings); });
    }

    private final Runnable launchSettings = () -> dispatchFireSettings();
    private void dispatchFireSettings() {
        if (!settingsOpening) return;
        settingsOpening = false;
        if (isFinishing() || isDestroyed() || isSettingsLaunchPaused) return;
        if (gear != null) gear.requestFocus();
        Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        try {
            android.content.ComponentName component = settings.resolveActivity(getPackageManager());
            if (component != null) {
                Log.i(LOG_TAG, "Resolved Fire TV Settings -> " + component.flattenToShortString());
                openIntent("Fire TV Settings", settings.setComponent(component));
                return;
            }
        } catch (RuntimeException error) {
            Log.w(LOG_TAG, "Cannot resolve Fire TV Settings");
        }
        openIntent("Fire TV Settings", null);
    }

    private void openIntent(String label, Intent intent) {
        if (intent != null) {
            try {
                startActivity(intent);
                android.content.ComponentName component = intent.getComponent();
                Log.i(LOG_TAG, "Opened " + label + " -> "
                        + (component == null ? "system resolved" : component.flattenToShortString()));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Log.w(LOG_TAG, "Activity rejected: " + label, ignored);
            }
        }
        Log.w(LOG_TAG, "No launchable activity: " + label);
        clearPending();
        Toast.makeText(this, "Could not open " + label, Toast.LENGTH_SHORT).show();
    }

}
