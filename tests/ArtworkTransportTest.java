package app.firetvremote.launcher.lan;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
public final class ArtworkTransportTest {
 static Transport server;static SSLContext tls;static int checks;
 static void check(boolean value){if(!value)throw new AssertionError("artwork HTTP guard");checks++;}
 static String request(String method,String path,String extra,byte[] bytes)throws Exception{
  try(SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket("127.0.0.1",server.getListeningPort())){
   socket.setSoTimeout(4000);socket.startHandshake();OutputStream out=socket.getOutputStream();
   String headers=method+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+server.getListeningPort()+"\r\n"+extra+"Content-Length: "+bytes.length+"\r\n\r\n";
   out.write(headers.getBytes("US-ASCII"));out.write(bytes);out.flush();ByteArrayOutputStream reply=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=socket.getInputStream().read(block))!=-1)reply.write(block,0,n);return reply.toString("UTF-8");
  }
 }
 public static void main(String[] args)throws Exception{
  KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(args[0])){keys.load(in,"test-only".toCharArray());}
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-only".toCharArray());
  TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
  tls=SSLContext.getInstance("TLSv1.2");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
  File root=Files.createTempDirectory(Paths.get(args[1]),"transport-").toFile();
  PairingStore phones=new PairingStore(new PairingStore.Disk(){public Map<String,String> load(){return new HashMap<>();}public boolean save(Map<String,String> state){return true;}});
  String token=new String(new char[64]).replace('\0','c');phones.add("11111111111111111111111111111111",token);
  LauncherBridge bridge=new LauncherBridge();bridge.management=new AppsEndpoint(1,Arrays.asList(new AppsEndpoint.App("tv.youtube","YouTube",null)));
  bridge.artwork=new MediaJobs(root,(input,output,kind,x,y)->Files.copy(input.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING),pkg->{});
  server=new Transport(InetAddress.getByName("127.0.0.1"),tls.getServerSocketFactory(),"artwork-test",true);server.pairingForTest(phones,null);server.enableEvents(bridge);
  byte[] image=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAC0lEQVR4XmNgQAYAAA4AAdXbrS0AAAAASUVORK5CYII=");
  String path="/v1/artwork/tv.youtube/tile",auth="Authorization: Bearer "+token+"\r\n",type="Content-Type: image/png\r\n";
  try{
   server.open();check(request("PUT",path,type,image).startsWith("HTTP/1.1 401"));
   check(request("PUT","/v1/artwork/tv.unknown/tile",auth+type,image).startsWith("HTTP/1.1 404"));
   check(request("PUT",path,auth+type,new byte[12]).startsWith("HTTP/1.1 400"));
   String reply=request("PUT",path,auth+type,image);check(reply.startsWith("HTTP/1.1 200"));
   String id=reply.replaceAll("(?s).*\\\"uploadId\\\":\\\"([0-9a-f]{32})\\\".*","$1");check(id.matches("[0-9a-f]{32}"));
   File target=MediaJobs.file(root,"tv.youtube","tile");check(!target.exists());
   check(request("POST",path+"/commit",auth+"X-Upload-Id: wrong\r\n",new byte[0]).startsWith("HTTP/1.1 400"));
   check(request("POST",path+"/commit",auth+"X-Upload-Id: "+id+"\r\n",new byte[0]).startsWith("HTTP/1.1 200"));check(target.exists());
   phones.revokeAll();check(request("PUT",path,auth+type,image).startsWith("HTTP/1.1 401"));
   System.out.println("PASS: "+checks+" artwork-only real TLS auth, stage, commit and revoked-token checks");
  }finally{server.stop();File[] files=root.listFiles();if(files!=null)for(File f:files)f.delete();root.delete();}
 }
}
