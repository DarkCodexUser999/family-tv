package app.firetvremote.launcher.lan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import javax.net.ssl.*;
import java.util.regex.*;

/** Real TLS + vendored NanoWSD + application dispatcher; synthetic keys only. */
public final class WssTransportTest {
    static int count; static SSLContext tls; static Transport server; static String host;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;}
    static SSLSocket socket()throws Exception{
        SSLSocket s=(SSLSocket)tls.getSocketFactory().createSocket("127.0.0.1",server.getListeningPort());s.setSoTimeout(3000);s.setEnabledProtocols(new String[]{"TLSv1.2"});s.startHandshake();return s;
    }
    static String header(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();int n;
        while((n=in.read())!=-1){b.write(n);String s=b.toString("US-ASCII");if(s.endsWith("\r\n\r\n"))return s;if(b.size()>8192)throw new IOException("header");}return b.toString("US-ASCII");
    }
    static SSLSocket upgrade(String token,int status)throws Exception{
        SSLSocket s=socket();String request="GET /v1/events HTTP/1.1\r\nHost: "+host+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nAuthorization: Bearer "+token+"\r\n\r\n";
        s.getOutputStream().write(request.getBytes("US-ASCII"));String response=header(s.getInputStream());
        check(response.startsWith("HTTP/1.1 "+status),"upgrade status "+status+": "+response);return s;
    }
    static String read(SSLSocket s)throws Exception{
        DataInputStream in=new DataInputStream(s.getInputStream());int first=in.readUnsignedByte(),length=in.readUnsignedByte();
        check((first&0x0f)==1&&(length&0x80)==0,"unmasked server text");length&=127;if(length==126)length=in.readUnsignedShort();check(length<=4096,"bounded server event");byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);
    }
    static void send(SSLSocket s,String value)throws Exception{
        byte[] bytes=value.getBytes("UTF-8"),mask={1,2,3,4};DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeByte(0x81);
        if(bytes.length<126)out.writeByte(0x80|bytes.length);else{out.writeByte(0xfe);out.writeShort(bytes.length);}out.write(mask);
        for(int i=0;i<bytes.length;i++)out.writeByte(bytes[i]^mask[i%4]);out.flush();
    }
    static long number(String json,String name){Matcher m=Pattern.compile("\""+name+"\":([0-9]+)").matcher(json);if(!m.find())throw new AssertionError(json);return Long.parseLong(m.group(1));}
    static void queued(RemoteBridgeTest.Ui ui)throws Exception{long until=System.nanoTime()+1000000000L;while(ui.tasks.isEmpty()&&System.nanoTime()<until)Thread.sleep(5);check(!ui.tasks.isEmpty(),"input reached main queue");}
    public static void main(String[] args)throws Exception{
        KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(Paths.get(args[0]))){keys.load(in,"test-only".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-only".toCharArray());
        KeyStore trust=KeyStore.getInstance("PKCS12");trust.load(null);trust.setCertificateEntry("test",keys.getCertificate("test"));
        TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
        tls=SSLContext.getInstance("TLSv1.2");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        PairingStore phones=new PairingStore(new PairingTest.Disk());String one=new String(new char[64]).replace('\0','c'),two=new String(new char[64]).replace('\0','d'),three=new String(new char[64]).replace('\0','e');
        phones.add("11111111111111111111111111111111",one);phones.add("22222222222222222222222222222222",two);phones.add("33333333333333333333333333333333",three);
        LauncherBridge bridge=new LauncherBridge();RemoteBridgeTest.Ui ui=new RemoteBridgeTest.Ui();bridge.resume(ui);
        server=new Transport(InetAddress.getByName("127.0.0.1"),tls.getServerSocketFactory(),"test-wss",true);server.pairingForTest(phones,null);server.enableEvents(bridge);
        try{
            server.open();host="127.0.0.1:"+server.getListeningPort();
            try(SSLSocket rejected=upgrade(new String(new char[64]).replace('\0','0'),401)){}
            try(SSLSocket a=upgrade(one,101);SSLSocket b=upgrade(two,101)){
                String state=read(a);read(b);check(state.contains("\"canNavigate\":true"),"foreground initial state");
                Path output=Paths.get(args[1]);Files.createDirectories(output);Files.write(output.resolve("state.json"),state.getBytes("UTF-8"));
                try(SSLSocket rejected=upgrade(three,503)){}
                try(SSLSocket http=socket()){
                    http.getOutputStream().write(("GET /v1/info HTTP/1.1\r\nHost: "+host+"\r\n\r\n").getBytes("US-ASCII"));
                    check(header(http.getInputStream()).startsWith("HTTP/1.1 200"),"HTTP available with two live WS peers");
                }
                // Refresh monotonic time after setup TLS handshakes; expiry tests cover stale input separately.
                bridge.focus("app.test.focus");state=read(a);read(b);
                String message=RemoteBridgeTest.message("wire-ok","OK",number(state,"serverTimeMs"),number(state,"epoch"));
                send(a,message);queued(ui);ui.drain();String ack=read(a);
                check(ack.contains("\"accepted\":true")&&ui.actions.size()==1,"wire input dispatched once");Files.write(output.resolve("ack.json"),ack.getBytes("UTF-8"));
                send(a,message);check(read(a).contains("\"accepted\":true")&&ui.actions.size()==1,"wire replay not redispatched");
                bridge.pause(ui);String paused=read(a);read(b);check(paused.contains("\"canNavigate\":false"),"pause event");
                send(a,RemoteBridgeTest.message("off","HOME",number(paused,"serverTimeMs"),number(paused,"epoch")));check(read(a).contains("ui_unavailable")&&ui.actions.size()==1,"no background HOME dispatch");
                bridge.resume(ui);read(a);read(b);
                a.setSoTimeout(200);try{a.getInputStream().read();throw new AssertionError("unexpected idle event");}catch(SocketTimeoutException expected){count++;}a.setSoTimeout(3000);
                phones.revokeAll();
                try{check(b.getInputStream().read()==-1,"revocation closed live socket");}catch(SocketException expected){count++;}
            }
            try(SSLSocket revoked=upgrade(one,401)){}
            phones.add("44444444444444444444444444444444",one);
            try(SSLSocket partial=upgrade(one,101)){
                read(partial);partial.getOutputStream().write(0x81);partial.getOutputStream().flush();
                try{check(partial.getInputStream().read()==-1,"partial frame deadline");}catch(SocketException expected){count++;}
            }
            try(SSLSocket oversized=upgrade(one,101)){
                read(oversized);oversized.getOutputStream().write(new byte[]{(byte)0x81,(byte)0xfe,0x10,1});oversized.getOutputStream().flush();
                try{check(oversized.getInputStream().read()==-1,"oversized frame closed before payload");}catch(SocketException expected){count++;}
            }
        }finally{server.stop();}
        check(server.activeClients()==0,"server shutdown released all readers");
        System.out.println("PASS: "+count+" real WSS auth/state/input/replay/revocation/deadline checks");
    }
}
