package app.firetvremote.launcher;
import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import app.firetvremote.launcher.lan.*;
import java.io.*;

/** Runs only inside the bounded HTTP worker; never on focus/UI dispatch. */
public final class ArtworkUploads {
 public static MediaJobs create(Context context){
  File root=new File(context.getFilesDir(),"remote-artwork");ArtworkFiles.privateRoot=root;
  return new MediaJobs(root,ArtworkUploads::normalize,ArtworkLoader::changed);
 }
 private static void normalize(File source,File target,String kind,int x,int y)throws IOException{
  try(RandomAccessFile input=new RandomAccessFile(source,"r")){
   int first=input.readUnsignedByte();
   if(first==255){input.seek(input.length()-2);if(input.readUnsignedShort()!=0xffd9)throw new IOException("truncated_jpeg");}
  }
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),o);
  try{UploadValidator.dimensions(o.outWidth,o.outHeight);}catch(IllegalArgumentException bad){throw new IOException("dimensions",bad);}
  int w="tile".equals(kind)?640:1280,h="tile".equals(kind)?360:480;
  o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;o.inSampleSize=1;
  while(o.outWidth/o.inSampleSize>Math.max(w,h)||o.outHeight/o.inSampleSize>Math.max(w,h))o.inSampleSize*=2;
  Bitmap bitmap=null,output=null;
  try{
   synchronized(ArtCache.DECODER){bitmap=BitmapFactory.decodeFile(source.getPath(),o);}
   if(bitmap==null)throw new IOException("decode");
   int orientation=1;try{orientation=new ExifInterface(source.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
   Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:break;}
   if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
   output=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);Canvas canvas=new Canvas(output);canvas.drawColor(Color.BLACK);
   float scale=Math.max(w/(float)bitmap.getWidth(),h/(float)bitmap.getHeight()),bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;
   float left=-(bw-w)*x/1000f,top=-(bh-h)*y/1000f;
   canvas.drawBitmap(bitmap,null,new RectF(left,top,left+bw,top+bh),new Paint(Paint.FILTER_BITMAP_FLAG));
   try(FileOutputStream out=new FileOutputStream(target)){if(!output.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("encode");out.getFD().sync();}
  }catch(OutOfMemoryError|RuntimeException bad){throw new IOException("decode",bad);}
  finally{if(bitmap!=null)bitmap.recycle();if(output!=null)output.recycle();}
 }
 private ArtworkUploads(){}
}
