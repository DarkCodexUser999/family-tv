package app.firetvremote.launcher.lan;
import java.io.*;
/** Bounded staging only: callers authenticate before entry and validate decoded pixels before commit. */
public final class UploadValidator {
 public static final int MAX_BYTES=2*1024*1024;
 public static void target(String pkg,String kind){
  if(!AppsEndpoint.packageName(pkg)||!("tile".equals(kind)||"backdrop".equals(kind)))throw new IllegalArgumentException("invalid_target");
 }
 public static void dimensions(int width,int height){
  if(width<1||height<1||(long)width*height>8000000L)throw new IllegalArgumentException("invalid_dimensions");
 }
 public static void stage(InputStream input,int length,File temporary)throws IOException{
  if(length<8||length>MAX_BYTES)throw new IOException("invalid_size");
  boolean valid=false;
  try(FileOutputStream out=new FileOutputStream(temporary)){
   byte[] signature=new byte[8];int offset=0,n;
   while(offset<8){n=input.read(signature,offset,8-offset);if(n<=0)throw new EOFException();offset+=n;}
   boolean png=(signature[0]&255)==137&&signature[1]==80&&signature[2]==78&&signature[3]==71&&signature[4]==13&&signature[5]==10&&signature[6]==26&&signature[7]==10;
   boolean jpeg=(signature[0]&255)==255&&(signature[1]&255)==216&&(signature[2]&255)==255;
   if(!png&&!jpeg)throw new IOException("invalid_format");
   out.write(signature);byte[] buffer=new byte[8192];int remaining=length-8;
   while(remaining>0){n=input.read(buffer,0,Math.min(buffer.length,remaining));if(n<=0)throw new EOFException();out.write(buffer,0,n);remaining-=n;}
   out.getFD().sync();valid=true;
  }finally{if(!valid)temporary.delete();}
 }
 private UploadValidator(){}
}
