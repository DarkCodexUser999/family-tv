package app.firetvremote.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.LruCache;
import android.view.View;

/** Draws a soft, pre-rendered wallpaper without a live blur pass. */
final class AmbientBackdrop extends View {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int NIGHT = 0xff080c17;
    private final LruCache<Integer, Bitmap> wallpapers = new LruCache<>(12);
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect bounds = new Rect();
    private Bitmap previous;
    private Bitmap current;
    private ValueAnimator fade;
    private int opacity = 255;

    AmbientBackdrop(Context context) {
        super(context);
    }

    void showAccent(int color) {
        Bitmap next = wallpapers.get(color);
        if (next == null) return;
        if (next == current) return;
        if (fade != null) fade.cancel();
        previous = opacity < 128 && previous != null ? previous : current;
        current = next;
        if (previous == null || previous == current) {
            opacity = 255;
            invalidate();
            return;
        }
        opacity = 0;
        fade = ValueAnimator.ofInt(0, 255);
        fade.setDuration(210);
        fade.addUpdateListener(animation -> {
            opacity = (int) animation.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    void prepareAccent(int color) {
        if (wallpapers.get(color) == null) wallpapers.put(color, wallpaper(color));
    }

    private Bitmap wallpaper(int color) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(NIGHT);
        Paint light = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        light.setShader(new RadialGradient(42, 7, 105,
                new int[] {color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, WIDTH, HEIGHT, light);
        light.setShader(new RadialGradient(149, 49, 79,
                new int[] {0xb52e487c, 0x002e487c}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, WIDTH, HEIGHT, light);
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (current == null) {
            canvas.drawColor(NIGHT);
            return;
        }
        bounds.set(0, 0, getWidth(), getHeight());
        if (previous != null && opacity < 255) {
            paint.setAlpha(255);
            canvas.drawBitmap(previous, null, bounds, paint);
        }
        paint.setAlpha(opacity);
        canvas.drawBitmap(current, null, bounds, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fade != null) fade.cancel();
        opacity = 255;
        super.onDetachedFromWindow();
    }
}
