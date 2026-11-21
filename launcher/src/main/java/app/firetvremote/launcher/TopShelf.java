package app.firetvremote.launcher;

import android.content.Context;
import android.graphics.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.*;
import java.util.*;
import java.util.concurrent.Future;

/** Displays the latest selection and owns its transitions and view callbacks. */
final class TopShelf extends FrameLayout {
    FocusTrace trace;

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawRect(0,0,getWidth(),getHeight(),shade);
        if (trace != null) trace.draw(displayed == null ? null : selection, false);
    }

    private final ArtworkLoader artwork;
    private boolean refreshing, refreshAgain;
    private final Map<String, Integer> rotations = new HashMap<>();
    private final Map<String, Integer> colors = new HashMap<>();
    private String selection;
    private String rotationOwner;
    private final android.content.BroadcastReceiver connectivity = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, android.content.Intent intent) { refresh(); }
    };
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        android.content.IntentFilter filter = new android.content.IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
        if (android.os.Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(connectivity, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(connectivity, filter);
    }
    @Override protected void onDetachedFromWindow() {
        getContext().unregisterReceiver(connectivity);
        super.onDetachedFromWindow();
    }
    private boolean rapid;
    private int tint;
    private final Paint gradient = new Paint();
    private final Paint shade = new Paint();
    private File desiredFile;
    private final Runnable settle = this::settleSelection;

    void refresh() {
        if (closed) return;
        if (refreshing) { refreshAgain = true; return; }
        refreshing = true;
        artwork.refresh(() -> post(() -> {
                if (!closed && !suspended && selection != null) settleSelection();
            }), () -> post(() -> {
                refreshing = false;
                if (!closed && refreshAgain) { refreshAgain = false; refresh(); }
            }));
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        updateGradient(tint);
        shade.setShader(new LinearGradient(0,h*.50f,0,Math.max(1,h),0x00000000,0xeb050609,Shader.TileMode.CLAMP));
    }
    private void updateGradient(int color) {
        tint = color;
        int dark = Color.rgb(Color.red(color)/3, Color.green(color)/3, Color.blue(color)/3);
        gradient.setShader(new LinearGradient(0,0,Math.max(1,getWidth()),Math.max(1,getHeight()),
                dark,0xff080a0f,Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0,0,getWidth(),getHeight(),gradient);
    }

    private final ImageView image;
    private final ImageView previous;
    private android.graphics.drawable.Drawable outgoing;
    private volatile String requested;
    private volatile boolean closed;
    private Future<?> pending;
    private String displayed;
    private volatile boolean suspended;

    TopShelf(Context context, ArtworkLoader artwork) {
        super(context);
        this.artwork = artwork;
        setWillNotDraw(false);
        setBackgroundColor(0xff050609);
        previous = new ImageView(context);
        previous.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(previous, new LayoutParams(-1,-1));
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new LayoutParams(-1, -1));
        image.setAlpha(0f);
    }

    void tint(String pkg, int color) {
        colors.put(pkg,color);
        if (pkg.equals(selection)) { updateGradient(color); invalidate(); }
    }
    void show(String pkg, boolean repeat) {
        if (closed || pkg == null || pkg.equals(selection)) return;
        selection = pkg;
        rotationOwner = null;
        rapid = repeat;
        requested = null;
        displayed = null;
        image.animate().cancel();
        outgoing = repeat ? null : image.getDrawable();
        previous.setImageDrawable(null);
        image.setImageDrawable(null);
        removeCallbacks(settle);
        if (!suspended) postOnAnimation(settle);
    }

    private void settleSelection() {
        if (closed || suspended || selection==null) return;
        Integer color = colors.get(selection);
        updateGradient(color == null ? 0xff374157 : color);
        File file = artwork.backdrop(selection);
        if (file == null) {
            List<File> files = artwork.images(selection);
            if (!files.isEmpty()) {
                Integer last=rotations.get(selection);
                int index=last==null ? 0 : (last + (selection.equals(rotationOwner) ? 0 : 1)) % files.size();
                file=files.get(index);
                rotations.put(selection,index);
                rotationOwner=selection;
            }
        }
        desiredFile = file;
        requested = "shelf/"+selection+"/"+(file==null ? "" : file.getName());
        invalidate();
        if (file==null) { present(requested,null); return; }
        showNow(requested);
    }

    void suspend() { suspended = true; removeCallbacks(settle); }
    void resume() { suspended = false; refresh(); if (selection!=null) postOnAnimation(settle); }

    // At most one decode in flight. New selections replace the desired key.
    private void showNow(String pkg) {
        if (closed || suspended || !pkg.equals(requested) || pkg.equals(displayed)) return;
        Bitmap hit = artwork.cached(pkg);
        if (hit != null) {
            present(pkg, hit);
            return;
        }
        if (pending != null) return;
        final File file = desiredFile;
        pending = artwork.submit(() -> {
            boolean decode = pkg.equals(requested) && !suspended && !closed;
            Bitmap art = decode ? artwork.loadBackdrop(pkg,file) : null;
            post(() -> {
                pending = null;
                if (closed || suspended) return;
                if (decode && pkg.equals(requested)) present(pkg, art);
                else if (requested != null) showNow(requested);
            });
        });
    }

    private void present(String pkg, Bitmap art) {
        displayed = pkg;
        beginTransition(art);
        image.setImageBitmap(art);
        image.animate().cancel();
        if (art != null && !rapid) {
            image.setAlpha(0f);
            image.animate().alpha(1f).setDuration(100).withEndAction(() -> previous.setImageDrawable(null)).start();
        } else image.setAlpha(1f);
        if (trace != null) invalidate();
    }

    private void beginTransition(Bitmap next) {
        previous.setImageDrawable(next == null || rapid ? null : outgoing);
        outgoing = null;
    }

    void close() {
        closed = true;
        suspend();
        outgoing = null;
        previous.setImageDrawable(null);
        image.setImageDrawable(null);
    }
}
