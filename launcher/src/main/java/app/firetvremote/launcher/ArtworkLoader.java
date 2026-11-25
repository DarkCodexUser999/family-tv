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
    private static final java.util.Set<ArtworkLoader> live=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<ArtworkLoader,Boolean>());
    private final java.lang.ref.WeakReference<android.app.Activity> activity;
    static void changed(String pkg){synchronized(live){for(ArtworkLoader loader:live)loader.update(pkg);}}
    private void update(String pkg){
        worker.execute(()->{cache.removePackage(pkg);synchronized(backdrops){backdrops.put(pkg,ArtworkFiles.backdrop(pkg));}
            android.app.Activity owner=activity.get();if(owner!=null&&!cancelled())owner.runOnUiThread(()->refreshViews(owner.getWindow().getDecorView(),pkg));});
    }
    private void refreshViews(android.view.View view,String pkg){
        if(cancelled())return;
        if(view instanceof TileButton && pkg.equals(((TileButton)view).pkg)){
            TileButton target=(TileButton)view;int w=target.getWidth(),h=target.getHeight();
            if(w>0&&h>0)worker.execute(()->{Bitmap bitmap=loadTile("app:"+pkg,pkg,w,h);target.post(()->{if(!cancelled()&&target.isAttachedToWindow()&&bitmap!=null)target.setArt(bitmap);});});
        }
        if(view instanceof TopShelf)((TopShelf)view).reload(pkg);
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)refreshViews(group.getChildAt(i),pkg);}
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ArtCache cache = new ArtCache();
    private final TileArt tiles;
    private final TmdbShelf provider;
    private final Map<String, ResolveInfo> icons = new HashMap<>();
    private final Map<String, File> backdrops = new HashMap<>();
    private volatile boolean closed;

    ArtworkLoader(Context context) {
        activity=new java.lang.ref.WeakReference<>(context instanceof android.app.Activity?(android.app.Activity)context:null);
        synchronized(live){live.add(this);}
        Context app = context.getApplicationContext();
        ArtworkFiles.privateRoot=new File(app.getFilesDir(),"remote-artwork");
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
    File backdrop(String pkg) { synchronized(backdrops){return backdrops.get(pkg);} }
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
        synchronized(live){live.remove(this);}
        provider.close();
        network.shutdownNow();
        worker.shutdownNow();
        cache.close();
    }
}
