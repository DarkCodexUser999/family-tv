package app.firetvremote.launcher;

import android.graphics.Bitmap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared decoded tile and shelf budget. Views may retain their currently displayed frame. */
final class ArtCache {
    static final Object DECODER = new Object();
    static final int BUDGET = 8 * 1024 * 1024;
    private final LinkedHashMap<String,Bitmap> entries = new LinkedHashMap<>(16, .75f, true);
    private int bytes, backdrops;
    private boolean closed;
    synchronized Bitmap get(String key) { return entries.get(key); }

    synchronized void put(String key, Bitmap value) {
        if (closed || value.getAllocationByteCount() > BUDGET) return;
        Bitmap previous = entries.put(key, value);
        boolean shelf = key.startsWith("shelf/");
        if (previous != null) bytes -= previous.getAllocationByteCount();
        else if (shelf) backdrops++;
        bytes += value.getAllocationByteCount();
        Iterator<Map.Entry<String,Bitmap>> it = entries.entrySet().iterator();
        while (it.hasNext() && (bytes > BUDGET || backdrops > 3)) {
            Map.Entry<String,Bitmap> item = it.next();
            boolean image = item.getKey().startsWith("shelf/");
            if (bytes <= BUDGET && !image) continue;
            bytes -= item.getValue().getAllocationByteCount();
            if (image) backdrops--;
            it.remove();
        }
    }
    synchronized void close() {
        closed = true;
        evictAll();
    }

    synchronized void evictAll() { entries.clear(); bytes = backdrops = 0; }
}
