package app.firetvremote.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.Button;

/** One control per tile; artwork is pre-rendered and the focus ring is allocation-free. */
final class TileButton extends Button {
    FocusTrace trace;
    String pkg;
    private final Drawable artwork;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds = new RectF();
    private final float radius;

    TileButton(Context context, Drawable artwork, float radius, float stroke) {
        super(context);
        this.artwork = artwork;
        this.radius = radius;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(0xccffffff);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = ring.getStrokeWidth() / 2f;
        ringBounds.set(inset, inset, width - inset, height - inset);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (getText().length() == 0 && artwork != null) {
            canvas.save();
            canvas.translate((getWidth() - artwork.getBounds().width()) / 2f,
                    (getHeight() - artwork.getBounds().height()) / 2f);
            artwork.draw(canvas);
            canvas.restore();
        }
        super.onDraw(canvas);
        if (isFocused()) {
            canvas.drawRoundRect(ringBounds, radius, radius, ring);
            if (trace != null) trace.draw(pkg, true);
        }
    }
}
