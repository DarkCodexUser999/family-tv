package app.firetvremote.launcher.lan;
import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
public final class ArtworkUploadTest {
 static int checks;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 interface Fail {void run()throws Exception;}
 static void reject(Fail action)throws Exception{try{action.run();throw new AssertionError("accepted invalid input");}catch(IOException|IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory(Paths.get(args[0]),"artwork-").toFile();File image=new File(root,"valid.png");
  try{
   ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",image);byte[] bytes=Files.readAllBytes(image.toPath());UploadValidator.image(image);checks++;
   File corrupt=new File(root,"bad.png");byte[] bad=bytes.clone();bad[bad.length-1]^=1;Files.write(corrupt.toPath(),bad);reject(()->UploadValidator.image(corrupt));
   File jpeg=new File(root,"sample.jpg");ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpeg",jpeg);UploadValidator.image(jpeg);checks++;
   Files.write(corrupt.toPath(),Arrays.copyOf(Files.readAllBytes(jpeg.toPath()),20));reject(()->UploadValidator.image(corrupt));
   final int[] changes={0};MediaJobs jobs=new MediaJobs(root,(src,dest,kind,x,y)->{UploadValidator.image(src);Files.copy(src.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);},pkg->changes[0]++);
   File target=MediaJobs.file(root,"tv.youtube","tile");Files.write(target.toPath(),new byte[]{7});
   reject(()->jobs.stage("phone","../bad","tile",new ByteArrayInputStream(bytes),bytes.length,500,500,()->true));
   reject(()->jobs.stage("phone","tv.youtube","tile",new ByteArrayInputStream(bytes),bytes.length,500,500,()->false));
   reject(()->jobs.stage("phone","tv.youtube","tile",new ByteArrayInputStream(bytes),bytes.length+1,500,500,()->true));
   check(Files.readAllBytes(target.toPath())[0]==7,"interruption retained previous");
   final String id=jobs.stage("phone","tv.youtube","tile",new ByteArrayInputStream(bytes),bytes.length,500,500,()->true);
   check(Files.readAllBytes(target.toPath())[0]==7,"staging never publishes");
   reject(()->jobs.commit("other","tv.youtube","tile",id,()->true));
   reject(()->jobs.commit("phone","tv.youtube","tile",id,()->false));
   jobs.cancel("phone",id);reject(()->jobs.commit("phone","tv.youtube","tile",id,()->true));
   check(Files.readAllBytes(target.toPath())[0]==7,"cancel retained previous");
   String second=jobs.stage("phone","tv.youtube","tile",new ByteArrayInputStream(bytes),bytes.length,500,500,()->true);
   jobs.commit("phone","tv.youtube","tile",second,()->true);check(Arrays.equals(Files.readAllBytes(target.toPath()),bytes),"atomic replacement");
   jobs.reset("tv.youtube","tile",()->true);check(!target.exists()&&changes[0]==2,"reset restores fallback");
   File quota=new File(root,"quota.jpg");try(RandomAccessFile out=new RandomAccessFile(quota,"rw")){out.setLength(32L*1024*1024);}
   MediaJobs low=new MediaJobs(new File(root.getPath()){@Override public long getUsableSpace(){return 0;}},(a,b,c,x,y)->{},p->{});
   reject(()->low.stage("phone","tv.youtube","tile",new ByteArrayInputStream(bytes),bytes.length,500,500,()->true));
   String over=jobs.stage("phone","tv.youtube","tile",new ByteArrayInputStream(bytes),bytes.length,500,500,()->true);reject(()->jobs.commit("phone","tv.youtube","tile",over,()->true));
   check(!target.exists(),"quota leaves prior default");
   System.out.println("PASS: "+checks+" artwork validation/staging/atomic/cancel/reset/quota guards; Android codec not executed on host");
  }finally{File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();root.delete();}
 }
}
