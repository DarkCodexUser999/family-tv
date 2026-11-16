package app.firetvremote.launcher.lan;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLServerSocketFactory;

/** Day 2 deliberately exposes no commands, credentials, files or app catalog. */
public final class Transport extends NanoHTTPD {
    private final String host;
    private final String info;
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
        info = "{\"apiVersion\":1,\"installationId\":\"" + installationId + "\",\"pairingOpen\":false}";
        setAsyncRunner(runner);
        makeSecure(tls, new String[] {"TLSv1.2"});
    }
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
                    session.execute();
                } catch (IOException | RuntimeException ignored) {
                    // Parser failures (e.g. malformed percent escapes) must not escape a worker.
                    // Invalid, timed out and disconnected clients receive no further work.
                } finally {
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
        // No routes beyond this minimal public identity exist until pairing/auth is implemented.
        if (!"/v1/info".equals(session.getUri())) return error(Response.Status.UNAUTHORIZED, "unauthorized");
        if (session.getMethod() != Method.GET || session.getQueryParameterString() != null
                || session.getHeaders().containsKey("transfer-encoding")
                || session.getHeaders().containsKey("expect")
                || session.getHeaders().containsKey("content-length") && !"0".equals(session.getHeaders().get("content-length"))) {
            return error(Response.Status.BAD_REQUEST, "invalid_request");
        }
        return json(Response.Status.OK, info);
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
