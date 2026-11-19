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
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
    private TopShelf backdrop;
    private FrameLayout scene;
    private FrameLayout rows;
    private final Map<LinearLayout, FrameLayout> trays = new LinkedHashMap<>();
    private float uiScale;
    private int activeRow;
    private RowFocusMemory rowFocus;
    private boolean fastNavigation;
    private long lastNavigationDown = -1;
    private View[] rowPreviews;
    private final List<List<TileData>> rowData = new ArrayList<>();
    private final List<List<Button>> rowButtons = new ArrayList<>();
    private final Map<LinearLayout,Integer> rowIndices = new LinkedHashMap<>();
    private final android.view.animation.Interpolator rowEase = new DecelerateInterpolator();

    private static final class TileData {
        String key, label, pkg;
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
        backdrop = new TopShelf(this);
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
        for(int i=0;i<rowData.size();i++) {
            rowButtons.add(null);
            if (i > 0) {
                rowPreviews[i] = createRowPreview(i);
                rows.addView(rowPreviews[i], rowPosition(i));
            }
        }
        List<Button> upper = rowData.isEmpty() ? java.util.Collections.emptyList() : ensureRow(0);
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
        if (!upper.isEmpty()) {
            firstFavorite = upper.get(0);
            gear.setNextFocusDownId(firstFavorite.getId());
            for (Button button : upper) button.setNextFocusUpId(gear.getId());
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

    private List<Button> ensureRow(int index) {
        List<Button> buttons=rowButtons.get(index);
        if(buttons==null) {
            if (rowPreviews[index] != null) rowPreviews[index].setAlpha(0f);
            buttons=addTray(rowData.get(index),index);
            rowButtons.set(index,buttons);
        }
        return buttons;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean direction = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean previous = fastNavigation;
        if (direction && event.getAction() == KeyEvent.ACTION_DOWN) {
            long now = event.getEventTime();
            fastNavigation = NavigationTiming.isRapid(lastNavigationDown, now, event.getRepeatCount());
            lastNavigationDown = now;
        }
        try { return super.dispatchKeyEvent(event); }
        finally { fastNavigation = previous; }
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if ((key==KeyEvent.KEYCODE_DPAD_DOWN || key==KeyEvent.KEYCODE_DPAD_UP) && focusTargets.containsValue(getCurrentFocus())) {
            int next=activeRow+(key==KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if(next>=0 && next<rowData.size()) {
                List<Button> buttons=ensureRow(next);
                int column = rowFocus.selection(next, rowFocus.selection(activeRow, 0));
                if (column >= 0) buttons.get(column).requestFocus();
                return true;
            }
        }
        return super.onKeyDown(key,event);
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

    private List<Button> addTray(List<TileData> tiles, int rowIndex) {
        FrameLayout tray = new FrameLayout(this);
        tray.setClipChildren(true); tray.setClipToPadding(true);
        tray.setAlpha(RowPresentation.alpha(rowIndex, activeRow));
        rows.addView(tray, rowPosition(rowIndex));
        LinearLayout strip = new LinearLayout(this);
        strip.setClipChildren(false); strip.setClipToPadding(false);
        strip.setPadding(px(34), px(32), px(34), px(32));
        FrameLayout clip = new FrameLayout(this);
        tray.addView(clip, new FrameLayout.LayoutParams(-1,-1));
        clip.setPadding(1, 0, 1, 0);
        clip.addView(strip, new FrameLayout.LayoutParams(px(tiles.size()*423+68), -1));
        trays.put(strip, tray); rowIndices.put(strip,rowIndex);
        List<Button> buttons = new ArrayList<>();
        for (TileData tile : tiles) {
            BitmapDrawable icon = new BitmapDrawable(getResources(), tile.icon);
            icon.setBounds(0,0,tile.icon.getWidth(),tile.icon.getHeight());
            buttons.add(addShortcut(strip, tile.key, tile.label, icon, tile.color, rowIndex == 0, tile.launch));
        }
        for (int i=0; i<buttons.size(); i++) {
            Button button = buttons.get(i);
            button.setNextFocusLeftId(buttons.get(Math.max(0,i-1)).getId());
            button.setNextFocusRightId(buttons.get(Math.min(buttons.size()-1,i+1)).getId());
        }
        tray.setBackground(traySurface(0xff506e9a));
        return buttons;
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

    private void focusScene(LinearLayout strip, Button button, String pkg) {
        int index = strip.indexOfChild(button);
        int maxScroll = Math.max(0, strip.getChildCount()*px(423) + px(30) - px(1728));
        int offset = Math.min(maxScroll, Math.max(0, index*px(423)-px(423)));
        strip.animate().cancel();
        if (fastNavigation) strip.setTranslationX(-offset);
        else if (strip.getTranslationX() != -offset) {
            strip.animate().translationX(-offset).setDuration(100).setInterpolator(rowEase).start();
        }
        int row = rowIndices.get(strip);
        rowFocus.remember(row, index);
        // Horizontal movement does not alter row visibility: avoid redundant fades.
        if (activeRow != row) {
            activeRow = row;
            rows.animate().cancel();
            float target = px(RowPresentation.translation(row));
            if (fastNavigation) rows.setTranslationY(target);
            else rows.animate().translationY(target).setDuration(100).setInterpolator(rowEase).start();
            for(Map.Entry<LinearLayout,FrameLayout> item:trays.entrySet()) {
                setRowAlpha(item.getValue(), RowPresentation.alpha(rowIndices.get(item.getKey()), row));
            }
            for (int r = 1; r < rowPreviews.length; r++) {
                setRowAlpha(rowPreviews[r], rowButtons.get(r) == null
                        ? RowPresentation.alpha(r, row) : 0f);
            }
        }
        backdrop.show(pkg);
    }

    private void setRowAlpha(View view, float target) {
        view.animate().cancel();
        if (fastNavigation) view.setAlpha(target);
        else if (view.getAlpha() != target) view.animate().alpha(target).setDuration(100).start();
    }

    private void restoreFocus(String key) {
        Button target = SETTINGS_KEY.equals(key) ? gear : focusTargets.get(key);
        // The saved app may live in a row whose interactive Views are still lazy.
        if (target == null && rowFocus != null) {
            int row = rowFocus.rowOf(key);
            if (row >= 0) {
                ensureRow(row);
                target = focusTargets.get(key);
            }
        }
        if (target == null || !target.requestFocus()) { if (firstFavorite != null) firstFavorite.requestFocus(); }
    }

    @Override
    protected void onPause() {
        isSettingsLaunchPaused = true;
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
        else if (firstFavorite != null) firstFavorite.requestFocus();
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
            if (firstFavorite != null) firstFavorite.requestFocus();
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
                else if (firstFavorite != null) firstFavorite.requestFocus();
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

    private Button addShortcut(LinearLayout row, String key, String label, Drawable icon, int color,
                               boolean featured, Runnable onSelect) {
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
        button.setBackground(null);
        button.setTextColor(PRIMARY_TEXT);
        button.setFocusable(true);
        button.setAlpha(.85f);
        button.setElevation(0);
        tileButton.setOnFocusChangeListener((view, focused) -> {
            tileButton.setAlpha(focused ? 1f : .8f);
            float scale = focused ? 1.08f : 1f;
            tileButton.animate().cancel();
            if (fastNavigation) {
                tileButton.setScaleX(scale); tileButton.setScaleY(scale);
            } else {
                tileButton.animate().scaleX(scale).scaleY(scale)
                        .setInterpolator(rowEase).setDuration(100).start();
            }
            if (focused) {
                focusedKey = key;
                app.firetvremote.launcher.lan.LauncherBridge.SHARED.focus(artworkPackage);
                focusScene(row, tileButton, artworkPackage);
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

        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(px(365), px(205));
        size.setMargins(0, 0, px(58), 0);
        row.addView(button, size);
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
