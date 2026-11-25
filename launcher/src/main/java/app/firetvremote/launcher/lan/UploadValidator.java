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
 public static void image(File file)throws IOException{
  if(file.length()<12||file.length()>MAX_BYTES)throw new IOException("invalid_image");
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   int first=in.readUnsignedByte();
   if(first==137){
    in.seek(0);byte[] magic=new byte[8];in.readFully(magic);
    byte[] expected={(byte)137,80,78,71,13,10,26,10};if(!java.util.Arrays.equals(magic,expected))throw new IOException("signature");
    boolean header=false,data=false;byte[] buffer=new byte[8192];
    while(in.getFilePointer()<in.length()){
     long size=in.readInt()&0xffffffffL;if(size>MAX_BYTES||size+8>in.length()-in.getFilePointer())throw new IOException("chunk");
     byte[] type=new byte[4];in.readFully(type);String name=new String(type,"US-ASCII");
     if(!header&&!name.equals("IHDR"))throw new IOException("header");
     java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(type);
     if(name.equals("IHDR")){
      if(header||size!=13)throw new IOException("header");byte[] bytes=new byte[13];in.readFully(bytes);crc.update(bytes);
      java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(bytes);try{dimensions(b.getInt(),b.getInt());}catch(IllegalArgumentException bad){throw new IOException("dimensions",bad);}header=true;
     }else{long remaining=size;while(remaining>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<1)throw new EOFException();crc.update(buffer,0,n);remaining-=n;}}
     if(crc.getValue()!=(in.readInt()&0xffffffffL))throw new IOException("crc");
     if(name.equals("IDAT"))data=true;
     if(name.equals("IEND")){if(size!=0||!data||in.getFilePointer()!=in.length())throw new IOException("end");return;}
    }throw new IOException("missing_end");
   }
   if(first!=255||in.readUnsignedByte()!=216)throw new IOException("signature");
   in.seek(in.length()-2);if(in.readUnsignedShort()!=0xffd9)throw new IOException("end");in.seek(2);boolean frame=false;
   while(in.getFilePointer()<in.length()-2){
    if(in.readUnsignedByte()!=255)throw new IOException("marker");int marker;do{marker=in.readUnsignedByte();}while(marker==255);
    if(marker==217)break;int length=in.readUnsignedShort();long end=in.getFilePointer()+length-2;
    if(length<2||end>in.length()-2)throw new IOException("segment");
    if(marker>=192&&marker<=207&&marker!=196&&marker!=200&&marker!=204){
     if(length<8)throw new IOException("frame");in.readUnsignedByte();int h=in.readUnsignedShort(),w=in.readUnsignedShort();
     try{dimensions(w,h);}catch(IllegalArgumentException bad){throw new IOException("dimensions",bad);}frame=true;
    }
    if(marker==218){if(!frame)throw new IOException("frame");return;}in.seek(end);
   }throw new IOException("missing_scan");
  }
 }
 private UploadValidator(){}
}
