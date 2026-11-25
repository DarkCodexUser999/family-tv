package app.firetvremote.launcher.lan;
import java.io.*;
import java.nio.file.*;
public final class UploadValidatorTest {
 public static void main(String[] args)throws Exception{
  File dir=Files.createTempDirectory("upload-validation").toFile(),temp=new File(dir,"staged");
  byte[] png={(byte)137,80,78,71,13,10,26,10,1};
  try{
   UploadValidator.target("tv.youtube","tile");UploadValidator.dimensions(4000,2000);
   for(String pkg:new String[]{"../escape","tv.a/path",""})try{UploadValidator.target(pkg,"tile");throw new AssertionError();}catch(IllegalArgumentException expected){}
   try{UploadValidator.dimensions(4001,2000);throw new AssertionError();}catch(IllegalArgumentException expected){}
   for(int length:new int[]{7,UploadValidator.MAX_BYTES+1,10})try{UploadValidator.stage(new ByteArrayInputStream(png),length,temp);throw new AssertionError();}catch(IOException expected){if(temp.exists())throw new AssertionError("partial retained");}
   try{UploadValidator.stage(new ByteArrayInputStream(new byte[9]),9,temp);throw new AssertionError();}catch(IOException expected){}
   UploadValidator.stage(new ByteArrayInputStream(png),9,temp);if(temp.length()!=9)throw new AssertionError();
   System.out.println("PASS: upload staging bounds, signature, traversal, interruption and pixel limits; full decoder validation remains required");
  }finally{temp.delete();dir.delete();}
 }
}
