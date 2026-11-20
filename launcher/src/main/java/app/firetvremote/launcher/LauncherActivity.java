package app.firetvremote.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
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
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;

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
    private boolean catalogFailed;
    private boolean isSettingsLaunchPaused;
    private boolean settingsOpening;
    private Button firstFavorite;
    private FocusTrace trace;
    private TopShelf backdrop;
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
    private int activeRow;
    private RowFocusMemory rowFocus;
    private boolean fastNavigation;
    private long lastNavigationDown = -1;
    private View[] rowPreviews;
    private final List<List<TileData>> rowData = new ArrayList<>();

    private static final class TileData {
        String key, label, pkg;
        Button button;
        Bitmap icon;
        int color;
        Runnable launch;
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
    };
    private RemoteServer remote;
    private int remoteSession;
    private AlertDialog remoteDialog;
    private final android.os.Handler pairUi=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pairExpiry=() -> { if(remoteDialog!=null) remoteDialog.dismiss(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        backdrop = new TopShelf(this);
        backdrop.trace = trace;
        scene.addView(backdrop, new FrameLayout.LayoutParams(-1, px(760)));

        setContentView(scene);
        final String restore = savedInstanceState == null
                ? getPreferences(MODE_PRIVATE).getString("last-focus", null)
                : savedInstanceState.getString("last-focus");
        lastSavedFocus = restore;
        backdrop.worker.execute(() -> {
            Map<String, AppCatalog.Entry> catalog;
            try {
                catalog = AppCatalog.list(this);
            } catch (RuntimeException error) {
                Log.w(LOG_TAG, "App discovery unavailable", error);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    catalogFailed = true;
                    buildHome();
                });
                return;
            }
            catalog.keySet().removeAll(hiddenPackages);
            LaunchRoute.preferPrimeApp(catalog.keySet());
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = getAssets().open("rows.json")) {
                    byte[] buffer = new byte[2048]; int count;
                    while ((count=in.read(buffer))!=-1) bytes.write(buffer,0,count);
                }
                org.json.JSONObject config = new org.json.JSONObject(bytes.toString("UTF-8"));
                org.json.JSONArray configured = config.getJSONArray("rows");
                // Validate all row definitions before removing anything from the catalog.
                List<List<String>> definitions = new ArrayList<>();
                for (int r = 0; r < Math.min(4, configured.length()); r++) {
                    org.json.JSONArray names = configured.getJSONObject(r).getJSONArray("packages");
                    List<String> packages = new ArrayList<>();
                    for (int i = 0; i < names.length(); i++) packages.add(names.getString(i));
                    definitions.add(packages);
                }
                for (List<String> packages : definitions) {
                    List<TileData> group = new ArrayList<>();
                    for (String pkg : packages) {
                        AppCatalog.Entry entry = catalog.remove(pkg);
                        if (entry != null) {
                            TileData tile = safeTileData(pkg, entry);
                            if (tile != null) group.add(tile);
                        }
                    }
                    rowData.add(group);
                }
            } catch(Exception e) { Log.w(LOG_TAG,"Rows config",e); }
            if(rowData.isEmpty()) rowData.add(new ArrayList<>());
            for(AppCatalog.Entry entry:catalog.values()) {
                String pkg=entry.info.activityInfo.packageName;
                TileData tile = safeTileData(pkg, entry);
                if (tile == null) continue;
                String label = tile.label.toLowerCase(java.util.Locale.ROOT);
                int target = rowData.size()-1;
                if(rowData.size()>1 && (label.contains("zee5") || label.contains("discovery"))) target=1;
                rowData.get(target).add(tile);
            }
            for(int r=rowData.size()-1;r>=0;r--) if(rowData.get(r).isEmpty()) rowData.remove(r);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                buildHome();
                restoreFocus(restore);
            });
        });
    }

    private TileData safeTileData(String pkg, AppCatalog.Entry entry) {
        try { return tileData("app:" + pkg, entry); }
        catch (RuntimeException error) {
            // A package can be removed/updated while the startup snapshot is being built.
            Log.w(LOG_TAG, "Skipping unavailable app " + pkg, error);
            return null;
        }
    }

    private TileData tileData(String key, AppCatalog.Entry entry) {
        TileData tile = new TileData();
        tile.key = key; tile.pkg = entry.info.activityInfo.packageName;
        tile.label = entry.info.loadLabel(getPackageManager()).toString();
        Drawable drawable = entry.info.loadIcon(getPackageManager());
        int iw = Math.max(1, drawable.getIntrinsicWidth()), ih = Math.max(1, drawable.getIntrinsicHeight());
        float factor = Math.min(322f/iw, 193f/ih);
        tile.icon = Bitmap.createBitmap(Math.max(1, Math.round(iw*factor)), Math.max(1, Math.round(ih*factor)), Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, tile.icon.getWidth(), tile.icon.getHeight());
        drawable.draw(new Canvas(tile.icon));
        tile.color = TopShelf.dominant(tile.icon);
        try { backdrop.prepare(tile.pkg, tile.icon, tile.color); }
        catch (RuntimeException unavailable) { Log.w(LOG_TAG, "Backdrop unavailable for " + tile.pkg); }
        int width = px(365), height = px(205);
        Bitmap flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(flat);
        android.graphics.Paint paint = new android.graphics.Paint(3);
        android.graphics.RectF rect = new android.graphics.RectF(0,0,width,height);
        paint.setColor(tile.color);
        canvas.drawRoundRect(rect, px(40), px(40), paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        Bitmap art = ArtworkFiles.decode(ArtworkFiles.tile(tile.pkg), width, height, Bitmap.Config.ARGB_8888);
        Drawable banner = entry.info.activityInfo.loadBanner(getPackageManager());
        if (banner == null) banner=entry.info.activityInfo.applicationInfo.loadBanner(getPackageManager());
        if (art == null && banner != null) {
            art=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            banner.setBounds(0,0,width,height); banner.draw(new Canvas(art));
        }
        if (art != null) {
            float fit=Math.max(width/(float)art.getWidth(),height/(float)art.getHeight());
            float w=art.getWidth()*fit,h=art.getHeight()*fit;
            canvas.drawBitmap(art,null,new android.graphics.RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint);
            art.recycle();
        } else {
            paint.setShader(new android.graphics.LinearGradient(0,0,width,height,tile.color,0xff141923,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(rect,paint); paint.setShader(null); paint.setXfermode(null);
            float fit=Math.min(width*.85f/tile.icon.getWidth(),height*.75f/tile.icon.getHeight());
            float w=tile.icon.getWidth()*fit,h=tile.icon.getHeight()*fit;
            canvas.drawBitmap(tile.icon,null,new android.graphics.RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint);
        }
        tile.icon.recycle(); tile.icon=flat;
        // Resolve the route and flags once, away from the key/click path.
        LaunchRoute route = LaunchRoute.build(tile.pkg, entry.info.activityInfo.name,
                entry.category, ownTaskPackages);
        if (route == null) return null;
        final Intent launch = route.intent();
        tile.launch = () -> openIntent(tile.label, launch);
        Log.i(LOG_TAG,"RowTile "+tile.pkg);

        return tile;
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
            tray.setBackground(traySurface(0xff506e9a));
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
        BitmapDrawable art = new BitmapDrawable(getResources(), tile.icon);
        art.setBounds(0, 0, tile.icon.getWidth(), tile.icon.getHeight());
        tile.button = addShortcut(row, column, tile.key, tile.label, art, tile.launch);
        tile.button.setTag(column);
        positionTile(row, column, tile.button);
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(px(365), px(205));
        size.topMargin = px(32);
        trays[row].addView(tile.button, size);
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
        final Bitmap[] images = new Bitmap[count];
        final android.graphics.Rect[] destinations = new android.graphics.Rect[count];
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < count; i++) {
            images[i] = tiles.get(i).icon;
            int left = px(34 + i * 423);
            destinations[i] = new android.graphics.Rect(left, px(32), left + px(365), px(237));
        }
        View preview = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                for (int i = 0; i < count; i++) canvas.drawBitmap(images[i], null, destinations[i], paint);
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

    private Drawable traySurface(int color) {
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
            rows.setTranslationY(px(RowPresentation.translation(row)));
            for (int i = 0; i < trays.length; i++) {
                float alpha = RowPresentation.alpha(i, row);
                if (trays[i].getAlpha() != alpha) trays[i].setAlpha(alpha);
                View preview = rowPreviews[i];
                if (preview != null && preview.getAlpha() != alpha) preview.setAlpha(alpha);
            }
        }
        backdrop.show(pkg);
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
        remoteSession++;
        if (remoteDialog != null) remoteDialog.dismiss();
        if (remote != null) {
            remote.close();
            remote = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        pairUi.removeCallbacks(pairExpiry);
        settingsOpening = false;
        scene.removeCallbacks(launchSettings);
        clearPending();
        if (backdrop != null) backdrop.close();
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

    private final app.firetvremote.launcher.lan.LauncherBridge.Ui lanUi=new app.firetvremote.launcher.lan.LauncherBridge.Ui(){
        @Override public void post(Runnable task){pairUi.post(task);}
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
                    if (choice == 0) app.firetvremote.launcher.lan.LanService.setEnabled(this, !enabled);
                    else if (choice == 1) showRemoteDialog(new AlertDialog.Builder(this)
                            .setTitle("Remote access").setMessage(app.firetvremote.launcher.lan.LanService.connectionDetails())
                            .setPositiveButton("Close", null));
                    else if(choice==2 || choice==3) {
                        if(!enabled) showRemoteDialog(new AlertDialog.Builder(this).setTitle("Remote access is OFF")
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
        });
        dialog.show();
    }

    private void showPairing() {
        if (remote == null) return;
        RemoteServer server = remote;
        String code = server.startPairing().replaceAll("(.{4})(?!$)", "$1 ");
        String address = localAddress();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pair a phone")
                .setMessage("TV address: " + address + "\nPairing code: " + code
                        + "\n\nEnter both in Family TV Remote within two minutes.")
                .setNegativeButton("Cancel", null).create();
        remoteDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            server.stopPairing();
            if (remoteDialog == dialog) remoteDialog = null;
        });
        dialog.show();
    }

    private String localAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                Enumeration<java.net.InetAddress> addresses = interfaces.nextElement().getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (IOException ignored) {}
        return "See Fire TV Settings › About › Network";
    }

    private void handleRemote(String action) {
        if (remote == null) return;
        handleFamilyAction(action);
    }
    private void handleFamilyAction(String action) {
        if ("HOME".equals(action)) {
            if (remoteDialog != null) remoteDialog.dismiss();
            focusFirstFavorite();
            return;
        }
        if ("AMAZON".equals(action)) {
            openIntent("Amazon Home", new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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

    private Button addShortcut(int row, int column, String key, String label, Drawable icon, Runnable onSelect) {
        Button button = new TileButton(this, icon, px(40), px(4));
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
        final String artworkPackage = "favorite:browser".equals(key) ? "com.amazon.cloud9"
                : key.substring(key.indexOf(':') + 1);
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
