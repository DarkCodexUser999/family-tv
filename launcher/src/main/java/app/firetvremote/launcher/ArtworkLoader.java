package app.firetvremote.launcher;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One Activity owns the workers and decoded images for its entire lifetime. */
final class ArtworkLoader implements AppCatalog.ArtworkSource {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ArtCache cache = new ArtCache();
    private final TileArt tiles;
    private final TmdbShelf provider;
    private final Map<String, ResolveInfo> icons = new HashMap<>();
    private final Map<String, File> backdrops = new HashMap<>();
    private volatile boolean closed;

    ArtworkLoader(Context context) {
        Context app = context.getApplicationContext();
        tiles = new TileArt(app);
        provider = new TmdbShelf(app);
    }

    Future<?> submit(Runnable task) { return worker.submit(task); }
    Future<?> submitCatalog(Runnable task) { return network.submit(task); }

    @Override public void register(String pkg, ResolveInfo info) {
        icons.put(pkg, info);
        backdrops.put(pkg, ArtworkFiles.backdrop(pkg));
    }

    // Catalog registration completes before rows are posted to the UI.
    File backdrop(String pkg) { return backdrops.get(pkg); }
    List<File> images(String pkg) { return provider.images(pkg); }

    void refresh(Runnable changed, Runnable finished) {
        network.execute(() -> {
            try {
                provider.refresh(changed);
            } finally {
                finished.run();
            }
        });
    }

    boolean cancelled() { return closed || Thread.currentThread().isInterrupted(); }
    Bitmap cached(String key) { return cache.get(key); }
    void trim() { cache.evictAll(); }

    Bitmap loadTile(String key, String pkg, int width, int height) {
        synchronized (ArtCache.DECODER) {
            if (cancelled()) return null;
            Bitmap bitmap = cache.get(key);
            if (bitmap == null) {
                bitmap = tiles.render(icons.get(pkg), width, height);
                if (bitmap != null) cache.put(key, bitmap);
            }
            return bitmap;
        }
    }

    static int dominant(Bitmap icon) {
        long r = 0, g = 0, b = 0, count = 0;
        for (int y = 0; y < icon.getHeight(); y += 6) for (int x = 0; x < icon.getWidth(); x += 6) {
            int c = icon.getPixel(x, y);
            int hi = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c)));
            int lo = Math.min(Color.red(c), Math.min(Color.green(c), Color.blue(c)));
            if (Color.alpha(c) < 128 || hi - lo < 35 || hi < 65) continue;
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); count++;
        }
        return count == 0 ? 0xff506e9a : Color.rgb((int)(r/count), (int)(g/count), (int)(b/count));
    }

    Bitmap loadBackdrop(String key, File file) {
        synchronized (ArtCache.DECODER) {
            return cancelled() ? null : decode(key, file);
        }
    }
    private Bitmap decode(String key, File file) {
        Bitmap bitmap = ArtworkFiles.decode(file, 1280, 720, Bitmap.Config.RGB_565);
        if (bitmap == null || cancelled()) return null;
        if (bitmap.getWidth()>1280 || bitmap.getHeight()>720 || bitmap.getConfig()!=Bitmap.Config.RGB_565) {
            Bitmap frame = Bitmap.createBitmap(1280,720,Bitmap.Config.RGB_565);
            float scale=Math.max(1280f/bitmap.getWidth(),720f/bitmap.getHeight());
            float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
            new Canvas(frame).drawBitmap(bitmap,null,new RectF((1280-w)/2,(720-h)/2,(1280+w)/2,(720+h)/2),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            bitmap.recycle();
            bitmap=frame;
        }
        cache.put(key,bitmap);
        return bitmap;
    }

    void close() {
        closed = true;
        provider.close();
        network.shutdownNow();
        worker.shutdownNow();
        cache.close();
    }
}
