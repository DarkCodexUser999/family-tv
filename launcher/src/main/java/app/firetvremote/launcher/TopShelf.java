package app.firetvremote.launcher;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** All artwork I/O, sampling and rasterization happens on the UI artwork worker. */
final class TopShelf extends FrameLayout {
    FocusTrace trace;
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (trace != null) trace.draw(displayed, false);
    }
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> cache = new LruCache<>(4);
    private final Map<String, File> sources = new HashMap<>();
    private final List<String> order = new ArrayList<>();
    private final ImageView image;
    private volatile String requested;
    private volatile boolean closed;
    private Future<?> pending;
    private String displayed;
    private volatile boolean suspended;

    TopShelf(Context context) {
        super(context);
        setBackgroundColor(0xff050609);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new LayoutParams(-1, -1));
        image.setAlpha(0f);
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

    void prepare(String pkg, Bitmap icon, int color) {
        File source = ArtworkFiles.backdrop(pkg);
        if (source == null) {
            int fingerprint = color;
            for (int y = 0; y < icon.getHeight(); y += 4) for (int x = 0; x < icon.getWidth(); x += 4) {
                fingerprint = 31 * fingerprint + icon.getPixel(x, y);
            }
            source = new File(getContext().getCacheDir(), "shelf-v2-" + pkg + ".jpg");
            File stamp = new File(getContext().getCacheDir(), "shelf-v2-" + pkg + ".stamp");
            boolean reusable = false;
            try (DataInputStream in = new DataInputStream(new FileInputStream(stamp))) {
                reusable = source.isFile() && source.length() > 0 && in.readInt() == fingerprint;
            } catch (IOException ignored) { }
            if (reusable) { sources.put(pkg, source); order.add(pkg); return; }
            Bitmap art = Bitmap.createBitmap(640, 240, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(art);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            paint.setShader(new LinearGradient(0, 0, 640, 240,
                    new int[]{color, mix(color, 0xff10152b, .48f), 0xff080b14}, null, Shader.TileMode.CLAMP));
            canvas.drawPaint(paint);
            paint.setShader(null); paint.setAlpha(48);
            // A low-resolution copy creates a soft graphic without a runtime blur.
            Bitmap soft = Bitmap.createScaledBitmap(icon, 32, 24, true);
            canvas.drawBitmap(soft, null, new Rect(340, -20, 670, 245), paint);
            soft.recycle(); paint.setAlpha(255);
            paint.setShader(new RadialGradient(370, 80, 400,
                    new int[]{0x00000000, 0xc9000000}, null, Shader.TileMode.CLAMP));
            canvas.drawPaint(paint);
            try {
                try (FileOutputStream out = new FileOutputStream(source)) {
                    if (!art.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw new IOException("JPEG write failed");
                }
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(stamp))) { out.writeInt(fingerprint); }
            } catch (IOException e) {
                stamp.delete();
                android.util.Log.w("FamilyTV", "Backdrop cache", e);
            }
            art.recycle();
        }
        sources.put(pkg, source); order.add(pkg);
    }

    static int mix(int a, int b, float t) {
        return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),
                (int)(Color.green(a)*(1-t)+Color.green(b)*t),
                (int)(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }

    private Bitmap load(String pkg) {
        Bitmap hit = cache.get(pkg);
        if (hit != null) return hit;
        File file = sources.get(pkg);
        if (file == null) return null;
        Bitmap decoded = ArtworkFiles.decode(file, 640, 240, Bitmap.Config.RGB_565);
        if (decoded == null) return null;
        Bitmap result = Bitmap.createBitmap(640, 240, Bitmap.Config.RGB_565);
        float scale = Math.max(640f/decoded.getWidth(), 240f/decoded.getHeight());
        float w = decoded.getWidth()*scale, h = decoded.getHeight()*scale;
        new Canvas(result).drawBitmap(decoded, null, new RectF((640-w)/2,(240-h)/2,(640+w)/2,(240+h)/2),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        decoded.recycle(); cache.put(pkg, result); return result;
    }

    private String queued;
    private final Runnable update = () -> showNow(queued);
    void show(String pkg) {
        if (closed || pkg == null) return;
        if (pkg.equals(queued) && !suspended && (pkg.equals(displayed) || pending != null && !pending.isDone())) return;
        // Invalidate an old in-flight result immediately, before the settle delay.
        requested = pkg;
        queued = pkg;
        removeCallbacks(update);
        if (!suspended && !closed) postDelayed(update,150);
    }
    void suspend() {
        suspended = true;
        removeCallbacks(update);
        if (pending != null) pending.cancel(false);
    }
    void resume() {
        suspended = false;
        if (queued != null && !closed) show(queued);
    }
    private void showNow(String pkg) {
        if (closed || suspended || !pkg.equals(requested) || pkg.equals(displayed)) return;
        if (pending != null) pending.cancel(false);
        pending = worker.submit(() -> {
            Bitmap art = load(pkg);
            if (closed || suspended || !pkg.equals(requested)) return;
            post(() -> {
                if (closed || suspended || !pkg.equals(requested)) return;
                displayed = pkg;
                image.setImageBitmap(art); image.setAlpha(1f);
                android.util.Log.i("FamilyTV", "Backdrop " + pkg + " 640x240 RGB_565");
            });
            int index = order.indexOf(pkg);
            for (int delta : new int[]{-1, 1}) {
                if (closed || suspended || !pkg.equals(requested)) return;
                int neighbour = index + delta;
                if (neighbour >= 0 && neighbour < order.size()) load(order.get(neighbour));
            }
        });
    }
    void close() {
        closed = true;
        suspend();
        image.setImageDrawable(null);
        // Evict after queued/running artwork work; otherwise an in-flight load can refill a closed cache.
        worker.execute(cache::evictAll);
        worker.shutdown();
    }
}
