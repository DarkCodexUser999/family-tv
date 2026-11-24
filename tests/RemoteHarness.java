package app.firetvremote.launcher.lan;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import javax.net.ssl.*;
import java.util.*;
import java.util.concurrent.*;

/** Host-only fake launcher UI behind the real TLS/NanoWSD application stack. */
public final class RemoteHarness {
    public static void main(String[] args)throws Exception{
        KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(Paths.get(args[0]))){keys.load(in,"test-only".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-only".toCharArray());
        SSLContext tls=SSLContext.getInstance("TLSv1.2");tls.init(km.getKeyManagers(),null,null);
        PairingStore phones=new PairingStore(new PairingStore.Disk(){
            public Map<String,String> load(){return new LinkedHashMap<>();}public boolean save(Map<String,String> value){return true;}
        });phones.add("11111111111111111111111111111111",new String(new char[64]).replace('\0','c'));
        LauncherBridge bridge=new LauncherBridge();ExecutorService main=Executors.newSingleThreadExecutor();
        bridge.apps=new AppsEndpoint(200,Arrays.asList(new AppsEndpoint.App("tv.youtube","YouTube",
                Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a2uoAAAAASUVORK5CYII="))));
        final String[] hiddenDisk={null};
        bridge.management=bridge.apps;
        bridge.hidden=new HiddenEndpoint(new RowsEndpoint.Disk(){public String read(){return hiddenDisk[0];}public void write(String value){hiddenDisk[0]=value;}},new HashSet<>(Arrays.asList("com.onemainstream.sonyliv.android")));
        final String[] rowDisk={null};
        bridge.rows=new RowsEndpoint(new RowsEndpoint.Disk(){
            public String read(){return rowDisk[0];}public void write(String json){rowDisk[0]=json;}
        },new RowsEndpoint.State(0,Arrays.asList(Arrays.asList("tv.youtube"))));
        Path output=Paths.get(args[2]);Files.write(output,new byte[0]);
        LauncherBridge.Ui ui=new LauncherBridge.Ui(){
            public void post(Runnable r){main.execute(r);}
            public boolean launch(String pkg){return perform("LAUNCH:"+pkg);}
            public boolean perform(String action){try{Files.write(output,(action+"\n").getBytes("UTF-8"),StandardOpenOption.APPEND);}catch(IOException e){throw new IllegalStateException(e);}return true;}
        };bridge.resume(ui);
        Transport server=new Transport(InetAddress.getByName(args[1]),tls.getServerSocketFactory(),"host-interop",phones,null,bridge);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {server.stop();main.shutdownNow();}));
        server.open();String fingerprint=PairingStore.hex(MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("test").getEncoded()));
        System.out.println("{\"port\":"+server.getListeningPort()+",\"fingerprint\":\""+fingerprint+"\"}");System.out.flush();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(System.in))){String line;
            while((line=reader.readLine())!=null){if(line.equals("pause"))main.submit(() -> bridge.pause(ui)).get();
                else if(line.equals("resume"))main.submit(() -> bridge.resume(ui)).get();else if(line.equals("revoke"))phones.revokeAll();else if(line.equals("stop"))break;}
        }finally{server.stop();main.shutdownNow();}
    }
}
