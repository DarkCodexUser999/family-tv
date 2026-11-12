package app.firetvremote.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class GlassTile extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final float radius;
    private final float stroke;
    private final int color;
    private final boolean focused;
    private Shader fill;
    private Shader glare;

    GlassTile(int color, boolean focused, float radius, float stroke) {
        this.color = color;
        this.focused = focused;
        this.radius = radius;
        this.stroke = stroke;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        float left = bounds.left + stroke / 2;
        float right = bounds.right - stroke / 2;
        float top = bounds.top + stroke / 2;
        float bottom = bounds.bottom - stroke / 2;
        shape.reset();
        if (right <= left || bottom <= top) return;
        float r = Math.min(radius, Math.min(right - left, bottom - top) / 2);
        float curve = r * 0.25f;
        shape.moveTo(left + r, top);
        shape.lineTo(right - r, top);
        shape.cubicTo(right - curve, top, right, top + curve, right, top + r);
        shape.lineTo(right, bottom - r);
        shape.cubicTo(right, bottom - curve, right - curve, bottom, right - r, bottom);
        shape.lineTo(left + r, bottom);
        shape.cubicTo(left + curve, bottom, left, bottom - curve, left, bottom - r);
        shape.lineTo(left, top + r);
        shape.cubicTo(left, top + curve, left + curve, top, left + r, top);
        shape.close();
        fill = new LinearGradient(left, top, right, bottom, color, 0xff151c2b, Shader.TileMode.CLAMP);
        glare = new LinearGradient(left, top, right, bottom,
                focused ? 0x76ffffff : 0x28ffffff, 0x00ffffff, Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(Canvas canvas) {
        if (shape.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffffffff);
        paint.setShader(fill);
        canvas.drawPath(shape, paint);
        paint.setShader(glare);
        canvas.drawPath(shape, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(focused ? 0x99ffffff : 0x43d4e4ff);
        canvas.drawPath(shape, paint);
    }

    @Override
    public void getOutline(Outline outline) {
        if (!getBounds().isEmpty()) outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter filter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
