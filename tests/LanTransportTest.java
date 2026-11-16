package app.firetvremote.launcher.lan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javax.net.ssl.*;

/** Real bounded HTTPS on loopback; never a claim about Android keystore/runtime behavior. */
public final class LanTransportTest {
    private static SSLContext tls;
    private static Transport server;
    private static String host;
    private static int count;
    private static final java.util.concurrent.atomic.AtomicReference<Throwable> uncaught =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        count++;
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        check(condition.getAsBoolean(), message);
    }
    private static SSLSocket socket() throws Exception {
        SSLSocket socket = (SSLSocket)tls.getSocketFactory().createSocket("127.0.0.1", server.getListeningPort());
        socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        socket.setSoTimeout(2500);
        socket.startHandshake(); // Trust only the test certificate, not a global trust-all manager.
        return socket;
    }
    private static String request(String method, String path, String headers) throws Exception {
        try (SSLSocket socket = socket()) {
            String req = method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\n" + headers + "\r\n";
            socket.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[512]; int n;
            while ((n=socket.getInputStream().read(buffer))!=-1) out.write(buffer,0,n);
            return out.toString("UTF-8");
        }
    }
    private static void headerChecks() throws Exception {
        check(ServerLimits.privateV4(InetAddress.getByName("10.0.0.1")), "private class A");
        check(ServerLimits.privateV4(InetAddress.getByName("172.31.255.1")), "private class B");
        for (String invalid : new String[]{"172.32.0.1","8.8.8.8","0.0.0.0","127.0.0.1","::1"}) {
            check(!ServerLimits.privateV4(InetAddress.getByName(invalid)), "non-private address accepted");
        }
        String header = "GET /v1/info HTTP/1.1\r\nHost: example\r\n\r\n";
        try (HeaderInput input = new HeaderInput(new ByteArrayInputStream((header+"BODY").getBytes("US-ASCII")))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
            while ((b=input.read())!=-1) out.write(b);
            check(out.toString("US-ASCII").equals(header), "body leaked into parser");
        }
        for (String bad : new String[]{"GET / HTTP/1.1\r\n", new String(new char[8193]).replace('\0','x'),
                "GET / HTTP/1.1\r\nHost: a\r\nHost: a\r\n\r\n",
                "GET / HTTP/1.1\r\nBad header\r\n\r\n"}) {
            try {
                new HeaderInput(new ByteArrayInputStream(bad.getBytes("US-ASCII"))).read();
                throw new AssertionError("unbounded/incomplete header accepted");
            } catch (IOException expected) { count++; }
        }
        AtomicLong now = new AtomicLong();
        HeaderInput expired = new HeaderInput(new ByteArrayInputStream(header.getBytes("US-ASCII")), now::get);
        now.set(ServerLimits.HEADER_TIMEOUT_NS);
        try { expired.read(); throw new AssertionError("header deadline ignored"); }
        catch (SocketTimeoutException expected) { count++; }
        expired.close();
    }
    public static void main(String[] args) throws Exception {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.set(error));
        headerChecks();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Paths.get(args[0]))) { keys.load(input, "test-only".toCharArray()); }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, "test-only".toCharArray());
        KeyStore trust = KeyStore.getInstance("PKCS12"); trust.load(null);
        trust.setCertificateEntry("test", keys.getCertificate("test"));
        TrustManagerFactory trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trusts.init(trust);
        tls = SSLContext.getInstance("TLSv1.2"); tls.init(keyManagers.getKeyManagers(), trusts.getTrustManagers(), null);
        try {
            new Transport(InetAddress.getLoopbackAddress(), tls.getServerSocketFactory(), "example-test");
            throw new AssertionError("production loopback binding accepted");
        } catch (IllegalArgumentException expected) { count++; }
        server = new Transport(InetAddress.getByName("127.0.0.1"), tls.getServerSocketFactory(), "example-test", true);
        try {
            server.open(); host = "127.0.0.1:" + server.getListeningPort();
            String info = request("GET", "/v1/info", "");
            check(info.startsWith("HTTP/1.1 200"), "info endpoint: " + info);
            check(!request("GET", "/v1/info", "Accept-Encoding: gzip\r\n").toLowerCase().contains("content-encoding: gzip"), "small info unnecessarily compressed");
            check(info.toLowerCase().contains("connection: close"), "keep-alive not disabled");
            String json = info.substring(info.indexOf("\r\n\r\n")+4);
            check(json.equals("{\"apiVersion\":1,\"installationId\":\"example-test\",\"pairingOpen\":false}"), "unexpected public info");
            Files.write(Paths.get(args[1]), json.getBytes(StandardCharsets.UTF_8));
            check(!request("GET", "/%ZZ", "").startsWith("HTTP/1.1 200"), "malformed escape accepted");
            check(request("GET", "/v1/info", "").startsWith("HTTP/1.1 200"), "parser failure broke service");
            check(uncaught.get() == null, "parser exception escaped worker");
            check(request("GET", "/v1/status", "").startsWith("HTTP/1.1 401"), "protected route exposed");
            check(request("POST", "/v1/pair", "Content-Length: 0\r\n").startsWith("HTTP/1.1 401"), "pairing prematurely exposed");
            check(request("GET", "/v1/info", "Origin: https://example.com\r\n").startsWith("HTTP/1.1 403"), "browser origin accepted");
            check(request("POST", "/v1/info", "").startsWith("HTTP/1.1 400"), "method accepted");
            check(request("GET", "/v1/info?token=example", "").startsWith("HTTP/1.1 400"), "query accepted");
            check(request("GET", "/v1/info", "Content-Length: 99\r\n").startsWith("HTTP/1.1 400"), "request body accepted");
            check(request("GET", "/v1/info", "Transfer-Encoding: chunked\r\n").startsWith("HTTP/1.1 400"), "chunked body accepted");
            try {
                String duplicate = request("GET", "/v1/info", "Host: other.example\r\n");
                check(!duplicate.startsWith("HTTP/1.1 200"), "duplicate host accepted");
            } catch (IOException rejected) { count++; }
            try (SSLSocket socket = socket()) {
                socket.getOutputStream().write(("GET /v1/info HTTP/1.1\r\nHost: "+host+"\r\nX: "+new String(new char[9000]).replace('\0','x')+"\r\n\r\n").getBytes("US-ASCII"));
                try { check(socket.getInputStream().read() == -1, "oversized wire header accepted"); }
                catch (IOException rejected) { count++; }
            }
            await(() -> server.activeClients()==0, "previous clients not released");
            try (Socket first = new Socket("127.0.0.1",server.getListeningPort());
                 Socket second = new Socket("127.0.0.1",server.getListeningPort())) {
                await(() -> server.activeClients()==2, "two bounded workers unavailable");
                try (Socket third = new Socket("127.0.0.1",server.getListeningPort())) {
                    third.setSoTimeout(1000);
                    check(third.getInputStream().read()==-1, "third client not rejected");
                }
                long before = System.nanoTime(); server.stop();
                check(System.nanoTime()-before < 1_000_000_000L, "shutdown blocked on stalled clients");
                check(!server.isAlive() && server.activeClients()==0, "stop left listener or clients");
            }
        } finally { server.stop(); }
        try (Socket unexpected = new Socket()) {
            unexpected.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(host.split(":")[1])),500);
            throw new AssertionError("disabled listener accepted connection");
        } catch (ConnectException expected) { count++; }
        Thread.setDefaultUncaughtExceptionHandler(previous);
        System.out.println("PASS: " + count + " LAN guards + real TLS info, rejects, capacity and shutdown checks");
    }
}
