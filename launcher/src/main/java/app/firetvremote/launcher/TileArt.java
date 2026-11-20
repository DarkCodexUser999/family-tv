package app.firetvremote.launcher;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class TileArt {
    private final Context app;
    TileArt(Context context) { app = context.getApplicationContext(); }

    Bitmap render(ResolveInfo info, int w, int h) {
        String pkg = info.activityInfo.packageName;
        File override = ArtworkFiles.tile(pkg);
        File disk = null;
        try {
            long version = app.getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime;
            String stamp = override == null ? "none" : override.length() + "-" + override.lastModified();
            File dir = new File(app.getCacheDir(), "tiles-v3/" + pkg);
            if (dir.isDirectory() || dir.mkdirs()) {
                disk = new File(dir, version + "-" + stamp + "-" + w + "x" + h + ".png");
                Bitmap saved = ArtworkFiles.decode(disk.isFile() ? disk : null, w, h, Bitmap.Config.ARGB_8888);
                if (saved != null) return saved;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException | SecurityException ignored) { }
        Bitmap tile = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tile);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF rect = new RectF(0, 0, w, h);
        float radius = w * 40f / 365;
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        Bitmap picture = ArtworkFiles.decode(override, w, h, Bitmap.Config.ARGB_8888);
        Drawable banner = null;
        if (picture == null) {
            banner = info.activityInfo.loadBanner(app.getPackageManager());
            if (banner == null) banner = info.activityInfo.applicationInfo.loadBanner(app.getPackageManager());
        }
        boolean icon = picture == null && banner == null;
        if (picture == null) {
            Drawable drawable = icon ? info.loadIcon(app.getPackageManager()) : banner;
            int iw = Math.max(1, drawable.getIntrinsicWidth()), ih = Math.max(1, drawable.getIntrinsicHeight());
            float scale = Math.min(w / (float)iw, h / (float)ih);
            picture = Bitmap.createBitmap(Math.max(1, Math.round(iw * scale)), Math.max(1, Math.round(ih * scale)), Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, picture.getWidth(), picture.getHeight());
            drawable.draw(new Canvas(picture));
        }
        float scale = h / (float)picture.getHeight();
        if (icon) {
            int edge = uniformEdge(picture);
            if (edge != 0) paint.setColor(edge);
            else paint.setShader(new LinearGradient(0, 0, w, h, TopShelf.dominant(picture), 0xff171a21, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            paint.setShader(null);
        } else scale = Math.max(scale, w / (float)picture.getWidth());
        float pw = picture.getWidth() * scale, ph = picture.getHeight() * scale;
        // SRC_ATOP preserves the pre-rendered rounded mask, including for transparent icons.
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        canvas.drawBitmap(picture, null, new RectF((w-pw)/2, (h-ph)/2, (w+pw)/2, (h+ph)/2), paint);
        picture.recycle();
        if (disk != null) {
            File temp = new File(disk.getParentFile(), "tile.tmp");
            try {
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    if (!tile.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException();
                }
                if (temp.renameTo(disk)) {
                    File[] old = disk.getParentFile().listFiles();
                    if (old != null) for (File f : old) if (!f.equals(disk)) f.delete();
                }
            } catch (IOException | SecurityException ignored) { } finally { temp.delete(); }
        }
        return tile;
    }

    private static int uniformEdge(Bitmap b) {
        int ref = b.getPixel(0,0);
        for (int i=0; i<=8; i++) {
            int x = i*(b.getWidth()-1)/8, y = i*(b.getHeight()-1)/8;
            for (int c : new int[]{b.getPixel(x,0), b.getPixel(x,b.getHeight()-1),
                    b.getPixel(0,y), b.getPixel(b.getWidth()-1,y)}) {
                if (Color.alpha(c)<240 || Math.abs(Color.red(c)-Color.red(ref))>24
                        || Math.abs(Color.green(c)-Color.green(ref))>24
                        || Math.abs(Color.blue(c)-Color.blue(ref))>24) return 0;
            }
        }
        return ref | 0xff000000;
    }
}
