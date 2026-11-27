package app.firetvremote.launcher.lan;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
public final class ProviderTransportTest {
 static Transport server;static SSLContext tls;static int checks;
 static void check(boolean value){if(!value)throw new AssertionError("provider HTTP guard");checks++;}
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
  final boolean[] enabled={false};
  bridge.apps=bridge.management;
  bridge.provider=new ProviderSettingsEndpoint(new ProviderSettingsEndpoint.Store(){
   public boolean enabled(){return enabled[0];}public boolean save(boolean value){enabled[0]=value;return true;}
  });
  String second=new String(new char[64]).replace('\0','d');phones.add("22222222222222222222222222222222",second);
  String auth="Authorization: Bearer "+token+"\r\n",other="Authorization: Bearer "+second+"\r\n";
  byte[] empty=new byte[0];String path="/v1/provider/refresh/tv.youtube";
  try{
   server.open();
   check(request("GET","/v1/status","",empty).startsWith("HTTP/1.1 401"));
   String status=request("GET","/v1/status",auth,empty);
   check(status.startsWith("HTTP/1.1 200") && status.contains("\"catalogRevision\":1") && status.contains("\"capabilities\""));
   check(!status.contains(token) && !status.contains("jobId") && status.length()<4096);
   check(request("POST","/v1/provider/enable","",empty).startsWith("HTTP/1.1 401"));
   check(request("GET","/v1/provider/enable",auth,empty).startsWith("HTTP/1.1 400"));
   check(request("POST",path,auth,empty).startsWith("HTTP/1.1 409"));
   check(request("POST","/v1/provider/enable",auth,new byte[]{1}).startsWith("HTTP/1.1 400"));
   check(request("POST","/v1/provider/enable?key=forbidden",auth,empty).startsWith("HTTP/1.1 400"));
   check(request("POST","/v1/provider/enable",auth,empty).startsWith("HTTP/1.1 200"));
   check(request("POST","/v1/provider/refresh/tv.missing",auth,empty).startsWith("HTTP/1.1 404"));
   String reply=request("POST",path,auth,empty);check(reply.contains("phone_required"));
   String id=reply.replaceAll("(?s).*\\\"jobId\\\":\\\"([0-9a-f-]{36})\\\".*","$1");check(id.matches("[a-f0-9-]{36}"));
   check(request("POST",path,other,empty).contains(id));
   String receipt="X-Job-Id: "+id+"\r\n";
   check(request("POST",path+"/claim",auth+receipt,empty).contains("running"));
   check(request("POST",path+"/claim",other+receipt,empty).startsWith("HTTP/1.1 409"));
   check(request("POST",path+"/complete",other+receipt,empty).startsWith("HTTP/1.1 409"));
   check(request("POST",path+"/canceled",auth+receipt,empty).contains("canceled"));
   check(request("POST","/v1/provider/disable",auth,empty).startsWith("HTTP/1.1 200"));
   check(request("GET","/v1/provider/refresh",auth,empty).startsWith("HTTP/1.1 409"));
   phones.revokeAll();check(request("POST","/v1/provider/enable",auth,empty).startsWith("HTTP/1.1 401"));
   System.out.println("PASS: "+checks+" provider/refresh real TLS auth, bounds and owner guards");
  }finally{server.stop();}
 }
}
