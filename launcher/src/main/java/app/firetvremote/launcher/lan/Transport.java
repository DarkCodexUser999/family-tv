package app.firetvremote.launcher.lan;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLServerSocketFactory;

/** Bounded TLS info and window-gated pairing; app/control routes remain unavailable. */
public final class Transport extends NanoHTTPD {
    private final String host;
    private final String installationId;
    private PairingStore phones;
    private PairingWindow pairing;
    private final ThreadLocal<String> peer = new ThreadLocal<>();
    private final BoundedRunner runner = new BoundedRunner();
    private final boolean testLoopback;

    public Transport(InetAddress address, SSLServerSocketFactory tls, String installationId) {
        this(address, tls, installationId, false);
    }
    Transport(InetAddress address, SSLServerSocketFactory tls, String installationId, boolean testLoopback) {
        super(address.getHostAddress(), 0);
        if (!ServerLimits.privateV4(address) && !(testLoopback && address.isLoopbackAddress())) {
            throw new IllegalArgumentException("A private Wi-Fi IPv4 address is required");
        }
        if (tls == null || !installationId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
        host = address.getHostAddress();
        this.testLoopback = testLoopback;
        this.installationId = installationId;
        setAsyncRunner(runner);
        makeSecure(tls, new String[] {"TLSv1.2"});
    }
    public Transport(InetAddress address, SSLServerSocketFactory tls, String id, PairingStore phones, PairingWindow pairing) {
        this(address,tls,id); this.phones=phones; this.pairing=pairing;
    }
    void pairingForTest(PairingStore phones, PairingWindow pairing) { this.phones=phones; this.pairing=pairing; }
    public void open() throws IOException {
        try { start(ServerLimits.READ_TIMEOUT_MS, true); }
        catch (IOException error) { stop(); throw error; }
    }
    @Override protected ClientHandler createClientHandler(Socket socket, InputStream input) {
        final HeaderInput limited = new HeaderInput(input);
        return new ClientHandler(limited, socket) {
            @Override public void close() {
                try { socket.close(); } catch (IOException ignored) { }
                try { limited.close(); } catch (IOException ignored) { }
            }
            @Override public void run() {
                try {
                    if (!ServerLimits.privateV4(socket.getInetAddress())
                            && !(testLoopback && socket.getInetAddress().isLoopbackAddress())) return;
                    // No reverse DNS lookup and no request-body temp-file manager.
                    HTTPSession session = new HTTPSession(new TempFileManager() {
                        @Override public void clear() { }
                        @Override public TempFile createTempFile(String hint) throws IOException {
                            throw new IOException("Request bodies are disabled");
                        }
                    }, limited, socket.getOutputStream());
                    peer.set(socket.getInetAddress().getHostAddress());
                    session.execute();
                } catch (IOException | RuntimeException ignored) {
                    // Parser failures (e.g. malformed percent escapes) must not escape a worker.
                    // Invalid, timed out and disconnected clients receive no further work.
                } finally {
                    peer.remove();
                    close();
                    runner.closed(this);
                }
            }
        };
    }
    @Override public Response serve(IHTTPSession session) {
        if (session.getHeaders().containsKey("origin")) return error(Response.Status.FORBIDDEN, "forbidden");
        if (!(host + ":" + getListeningPort()).equals(session.getHeaders().get("host"))) {
            return error(Response.Status.BAD_REQUEST, "invalid_request");
        }
        if (session.getQueryParameterString()!=null || session.getHeaders().containsKey("transfer-encoding")
                || session.getHeaders().containsKey("expect")) return error(Response.Status.BAD_REQUEST,"invalid_request");
        if ("/v1/pair".equals(session.getUri()) && pairing!=null) return pair(session);
        if (!"/v1/info".equals(session.getUri())) {
            if(AuthFilter.phone(phones,session.getHeaders().get("authorization"))==null)
                return error(Response.Status.UNAUTHORIZED,"unauthorized");
            // No WebSocket implementation yet; authenticated upgrades cannot bypass this gate.
            return error(Response.Status.NOT_FOUND,"not_found");
        }
        if(session.getMethod()!=Method.GET || session.getHeaders().containsKey("content-length")
                && !"0".equals(session.getHeaders().get("content-length"))) return error(Response.Status.BAD_REQUEST,"invalid_request");
        String id=pairing==null?null:pairing.sessionId();
        return json(Response.Status.OK,"{\"apiVersion\":1,\"installationId\":\""+installationId
                +"\",\"pairingOpen\":"+(id!=null)+(id==null?"":",\"pairingSessionId\":\""+id+"\"")+"}");
    }
    private Response pair(IHTTPSession session) {
        if(session.getMethod()!=Method.POST || !"application/json".equals(session.getHeaders().get("content-type")))
            return error(Response.Status.BAD_REQUEST,"invalid_request");
        try {
            String length=session.getHeaders().get("content-length");
            if(length==null || !length.matches("[0-9]{1,4}")) throw new IllegalArgumentException();
            int size=Integer.parseInt(length);
            if(size<1 || size>1024) throw new IllegalArgumentException();
            byte[] body=new byte[size]; int offset=0,n;
            while(offset<size) {
                n=session.getInputStream().read(body,offset,size-offset);
                if(n<0) throw new IOException("Short JSON body"); offset+=n;
            }
            java.nio.charset.CharsetDecoder utf8=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            String result=pairing.pair(peer.get(),PairJson.parse(utf8.decode(java.nio.ByteBuffer.wrap(body)).toString()));
            if(result.startsWith("{")) return json(Response.Status.OK,result);
            return error(result.equals("rate_limited")?Response.Status.TOO_MANY_REQUESTS:
                    result.equals("busy")?Response.Status.SERVICE_UNAVAILABLE:Response.Status.UNAUTHORIZED,result);
        } catch(IOException | IllegalArgumentException bad) { return error(Response.Status.BAD_REQUEST,"invalid_request"); }
    }
    @Override protected boolean useGzipWhenAccepted(Response response) { return false; }
    private Response error(Response.Status status, String code) {
        return json(status, "{\"apiVersion\":1,\"requestId\":\"unavailable\",\"error\":{\"code\":\""
                + code + "\",\"message\":\"Request unavailable\"}}");
    }
    private Response json(Response.Status status, String body) {
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", body);
        response.closeConnection(true);
        response.addHeader("Cache-Control", "no-store");
        return response;
    }
    int activeClients() { return runner.active(); }
}
