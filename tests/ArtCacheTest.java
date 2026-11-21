package app.firetvremote.launcher;

import android.graphics.Bitmap;

public class ArtCacheTest {
    public static void main(String[] args) {
        int frameBytes = 1280*720*2;
        ArtCache cache = new ArtCache();
        Bitmap frame = new Bitmap(frameBytes);
        for (int i=0;i<4;i++) cache.put("shelf/"+i,frame);
        if (cache.get("shelf/0") != null || cache.get("shelf/1") == null) throw new AssertionError();
        cache.put("visible", new Bitmap(4*1024*1024));
        if (cache.get("shelf/2") != null || cache.get("visible")==null) throw new AssertionError();
        cache.put("visible", new Bitmap(512));
        cache.put("another", new Bitmap(4*1024*1024));
        if (cache.get("visible")==null) throw new AssertionError();
        cache.evictAll();
        if (cache.get("visible")!=null || cache.get("another")!=null) throw new AssertionError();
        cache.close();
        cache.put("late-result", frame);
        if (cache.get("late-result") != null) throw new AssertionError("Closed cache was refilled");
        System.out.println("PASS: decoded byte budget, three-backdrop cap, replacement and trim");
    }
}
