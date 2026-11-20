package app.firetvremote.launcher;

import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/** Debug-build, UI-thread timestamps. Draw callbacks measure submission, not panel presentation. */
final class FocusTrace {
    private long sequence, down, ring = -1, art = -1;
    private int key, repeat;
    private String target;

    void down(KeyEvent event) {
        sequence++;
        down = event.getEventTime();
        key = event.getKeyCode();
        repeat = event.getRepeatCount();
        target = null;
        ring = art = -1;
    }

    void select(String pkg) { if (sequence != 0) target = pkg; }

    void draw(String pkg, boolean outline) {
        if (target == null || !target.equals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        if (outline && ring < 0) ring = now;
        else if (!outline && art < 0) art = now;
        else return;
        Log.d("FamilyTVTiming", "id=" + sequence + " key=" + key + " repeat=" + repeat
                + " outlineMs=" + (ring < 0 ? -1 : ring - down)
                + " artMs=" + (art < 0 ? -1 : art - down)
                + " ringToArtMs=" + (art < 0 || ring < 0 ? -1 : Math.max(0, art - ring)));
    }
}
