package app.firetvremote.launcher.lan;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.security.SecureRandom;

/** One staged job, two-phase commit. Disconnected uploads never automatically replace artwork. */
public final class MediaJobs {
 public interface Guard { boolean valid(); }
 public interface Processor { void normalize(File input,File output,String kind,int x,int y)throws IOException; }
 public interface Changed { void changed(String pkg); }
 private final File root;private final Processor processor;private final Changed changed;
 private final Semaphore busy=new Semaphore(1);private final SecureRandom random=new SecureRandom();
 private File pending;private String owner,id,pkg,kind;private long expiry;
 public MediaJobs(File root,Processor processor,Changed changed){this.root=root;this.processor=processor;this.changed=changed;}
 private void ready()throws IOException{if(!root.isDirectory()&&!root.mkdirs())throw new IOException("storage");
  File[] old=root.listFiles();if(old==null)throw new IOException("storage");for(File f:old)if(f.getName().endsWith(".tmp")&&!f.equals(pending))f.delete();}
 public synchronized String stage(String owner,String pkg,String kind,InputStream in,int size,int x,int y,Guard guard)throws IOException{
  UploadValidator.target(pkg,kind);if(x<0||x>1000||y<0||y>1000)throw new IOException("crop");
  if(!guard.valid())throw new IOException("unauthorized");ready();
  if(root.getUsableSpace()<8L*1024*1024)throw new IOException("storage");
  if(pending!=null&&!owner.equals(this.owner)&&System.nanoTime()<expiry)throw new IOException("busy");
  discard();File source=File.createTempFile("source-",".tmp",root),output=File.createTempFile("image-",".tmp",root);
  try{
   UploadValidator.stage(in,size,source);UploadValidator.image(source);processor.normalize(source,output,kind,x,y);
   if(output.length()<1||output.length()>UploadValidator.MAX_BYTES||!guard.valid())throw new IOException("invalid_image");
   byte[] token=new byte[16];random.nextBytes(token);StringBuilder hex=new StringBuilder();for(byte b:token)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   pending=output;this.owner=owner;this.pkg=pkg;this.kind=kind;id=hex.toString();expiry=System.nanoTime()+60000000000L;return id;
  }finally{source.delete();if(pending!=output)output.delete();}
 }
 public synchronized void commit(String owner,String pkg,String kind,String id,Guard guard)throws IOException{
  UploadValidator.target(pkg,kind);
  if(pending==null||!owner.equals(this.owner)||!pkg.equals(this.pkg)||!kind.equals(this.kind)||!this.id.equals(id)||System.nanoTime()>expiry||!guard.valid())throw new IOException("expired");
  File target=file(root,pkg,kind);long used=0;File[] files=root.listFiles();if(files==null)throw new IOException("storage");
  for(File f:files)if(f.getName().endsWith(".jpg")&&!f.equals(target))used+=f.length();
  if(used+pending.length()>32L*1024*1024)throw new IOException("quota");
  if(!pending.renameTo(target))throw new IOException("storage");pending=null;changed.changed(pkg);
 }
 public synchronized void cancel(String owner,String id){if(owner.equals(this.owner)&&this.id!=null&&this.id.equals(id))discard();}
 public synchronized void reset(String pkg,String kind,Guard guard)throws IOException{
  UploadValidator.target(pkg,kind);if(!guard.valid())throw new IOException("unauthorized");File f=file(root,pkg,kind);
  if(f.exists()&&!f.delete())throw new IOException("storage");if(pkg.equals(this.pkg)&&kind.equals(this.kind))discard();changed.changed(pkg);
 }
 private void discard(){if(pending!=null)pending.delete();pending=null;id=null;}
 public static File file(File root,String pkg,String kind){UploadValidator.target(pkg,kind);return new File(root,pkg+"-"+kind+".jpg");}
 public boolean acquire(){return busy.tryAcquire();}public void release(){busy.release();}
}
