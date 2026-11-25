package app.firetvremote.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import java.io.File;

final class ArtworkFiles {
    static volatile File privateRoot;
    static File tile(String pkg) {
        File local=find("tiles",pkg,".png");
        return local!=null?local:uploaded(pkg,"tile");
    }

    static File backdrop(String pkg) {
        File jpeg = find("backdrops", pkg, ".jpg");
        File png=find("backdrops",pkg,".png");
        return jpeg!=null?jpeg:png!=null?png:uploaded(pkg,"backdrop");
    }

    private static File find(String directory, String pkg, String extension) {
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "FamilyTV");
            File file = new File(new File(root, directory), pkg + extension);
            return file.isFile()&&file.canRead()?file:null;
        } catch (SecurityException denied) {
            return null;
        }
    }

    private static File uploaded(String pkg,String kind){File root=privateRoot;if(root==null)return null;File f=new File(root,pkg+"-"+kind+".jpg");return f.isFile()&&f.canRead()?f:null;}

    static Bitmap decode(File file, int width, int height, Bitmap.Config format) {
        if (file == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > width * 2
                    || options.outHeight / options.inSampleSize > height * 2) {
                options.inSampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = format;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
