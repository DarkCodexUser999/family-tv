package app.firetvremote.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class HomeService extends AccessibilityService {
    private static final String TAG = "FamilyTVHome";
    private static final long DOUBLE_PRESS_MS = 350;
    private static final long STOCK_GRACE_MS = 2000;
    private Intent stockHome;
    private long previousDown = -1;
    private long stockUntil;
    private boolean consumedDown;

    @Override
    protected void onServiceConnected() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved != null && resolved.activityInfo != null) {
            android.content.pm.ActivityInfo activity = resolved.activityInfo;
            if (!getPackageName().equals(activity.packageName)
                    && !"android".equals(activity.packageName)
                    && (activity.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                stockHome = home.setComponent(new ComponentName(activity.packageName, activity.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        }
        Log.i(TAG, "Bound; stock Home=" + (stockHome == null ? "unresolved" : stockHome.getComponent()));
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME || stockHome == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean consume = consumedDown;
            consumedDown = false;
            return consume;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() != 0) return consumedDown;

        long now = SystemClock.uptimeMillis();
        if (now < stockUntil) return false;
        long down = event.getEventTime();
        boolean doublePress = previousDown >= 0 && down >= previousDown
                && down - previousDown <= DOUBLE_PRESS_MS;
        Intent next = doublePress ? stockHome : new Intent(this, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(next);
            consumedDown = true;
            previousDown = doublePress ? -1 : down;
            if (doublePress) stockUntil = now + STOCK_GRACE_MS;
            Log.i(TAG, doublePress ? "Double Home -> stock" : "Single Home -> Family TV");
            return true;
        } catch (RuntimeException failure) {
            consumedDown = false;
            previousDown = -1;
            Log.w(TAG, "Home launch failed; leaving key to Fire OS", failure);
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
