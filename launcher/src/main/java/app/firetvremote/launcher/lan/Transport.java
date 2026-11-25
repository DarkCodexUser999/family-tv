package app.firetvremote.launcher.lan;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLServerSocketFactory;

/** Bounded TLS pairing, cached status and authenticated foreground-only WSS controls. */
public final class Transport extends NanoWSD {
    private final String host;
    private final String installationId;
    private PairingStore phones;
    private PairingWindow pairing;
    private EventHub events;
    private LauncherBridge bridge;
    private AppLaunch launches;
    private final Semaphore httpSlots=new Semaphore(2);
    private final ThreadLocal<Connection> connection=new ThreadLocal<>();
    private static final class Connection {
        Socket socket;HeaderInput input;boolean permit=true;EventHub.Peer peer;
    }
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
    public Transport(InetAddress address,SSLServerSocketFactory tls,String id,PairingStore phones,PairingWindow pairing,LauncherBridge bridge) {
        this(address,tls,id,phones,pairing); enableEvents(bridge);
    }
    void enableEvents(LauncherBridge bridge) {
        this.bridge=bridge;launches=new AppLaunch(bridge);runner.enableEvents();events=new EventHub(bridge,() -> System.nanoTime()/1000000L);
        if(phones!=null)phones.onRevoked=events::revoke;
    }
    @Override public void stop(){
        if(events!=null)events.close();super.stop();
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
                Connection current=null;
                try {
                    if(!httpSlots.tryAcquire())return;
                    current=new Connection();current.socket=socket;current.input=limited;connection.set(current);
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
                    peer.remove();connection.remove();
                    if(current!=null){if(current.permit)httpSlots.release();if(current.peer!=null && events!=null)events.remove(current.peer);sockets.remove(current);}
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
        if (session.getQueryParameterString()!=null && !"/v1/apps".equals(session.getUri()) && !"/v1/hidden/apps".equals(session.getUri()) || session.getHeaders().containsKey("transfer-encoding")
                || session.getHeaders().containsKey("expect")) return error(Response.Status.BAD_REQUEST,"invalid_request");
        if ("/v1/pair".equals(session.getUri()) && pairing!=null) return pair(session);
        if (!"/v1/info".equals(session.getUri())) {
            String phone=AuthFilter.phone(phones,session.getHeaders().get("authorization"));
            if(phone==null)return error(Response.Status.UNAUTHORIZED,"unauthorized");
            if(bridge!=null && session.getUri().startsWith("/v1/artwork/"))return artwork(session,phone);
            if(bridge!=null && session.getUri().startsWith("/v1/hidden"))return hidden(session,phone);
            if(bridge!=null && "/v1/rows".equals(session.getUri()))return rows(session,phone);
            if(bridge!=null && session.getUri().startsWith("/v1/apps"))return apps(session,phone);
            if(events!=null && "/v1/status".equals(session.getUri()) && session.getMethod()==Method.GET && emptyBody(session))
                return json(Response.Status.OK,events.status());
            if(events!=null && "/v1/events".equals(session.getUri()))return upgrade(session,phone);
            return error(Response.Status.NOT_FOUND,"not_found");
        }
        if(session.getMethod()!=Method.GET || session.getHeaders().containsKey("content-length")
                && !"0".equals(session.getHeaders().get("content-length"))) return error(Response.Status.BAD_REQUEST,"invalid_request");
        String id=pairing==null?null:pairing.sessionId();
        return json(Response.Status.OK,"{\"apiVersion\":1,\"installationId\":\""+installationId
                +"\",\"pairingOpen\":"+(id!=null)+(id==null?"":",\"pairingSessionId\":\""+id+"\"")+"}");
    }
    private Response apps(IHTTPSession session,String phone) {
        AppsEndpoint snapshot=bridge.apps;
        if(snapshot==null)return error(Response.Status.SERVICE_UNAVAILABLE,"catalog_unavailable");
        String path=session.getUri();
        if(path.startsWith("/v1/apps/") && path.endsWith("/launch") && path.length()>16)return launch(session,phone,path);
        if(session.getMethod()!=Method.GET || !emptyBody(session))return error(Response.Status.BAD_REQUEST,"invalid_request");
        if(path.equals("/v1/apps")) {
            String query=session.getQueryParameterString();
            // Only canonical query strings; reject duplicate, unknown and encoded parameters.
            if(query!=null && !query.matches("(?:cursor=[0-9]{1,15}_[0-9]{1,2}(?:&limit=(?:[1-9]|1[0-9]|2[0-5]))?|limit=(?:[1-9]|1[0-9]|2[0-5]))"))
                return error(Response.Status.BAD_REQUEST,"invalid_request");
            String cursor=null;int limit=25;
            if(query!=null)for(String field:query.split("&")) {
                if(field.startsWith("cursor="))cursor=field.substring(7);
                else if(field.startsWith("limit="))limit=Integer.parseInt(field.substring(6));
            }
            try{return json(Response.Status.OK,snapshot.page(cursor,limit));}
            catch(IllegalStateException stale){return error(Response.Status.CONFLICT,"stale_catalog");}
            catch(IllegalArgumentException bad){return error(Response.Status.BAD_REQUEST,"invalid_request");}
        }
        if(path.startsWith("/v1/apps/") && path.endsWith("/icon") && path.length()>14) {
            String pkg=path.substring(9,path.length()-5);
            byte[] icon=AppsEndpoint.packageName(pkg)?snapshot.icon(pkg):null;
            if(icon!=null) {
                Response response=newFixedLengthResponse(Response.Status.OK,"image/png",new java.io.ByteArrayInputStream(icon),icon.length);
                response.closeConnection(true);response.addHeader("Cache-Control","no-store");return response;
            }
        }
        return error(Response.Status.NOT_FOUND,"not_found");
    }
    private Response launch(IHTTPSession session,String phone,String path) {
        String pkg=path.substring(9,path.length()-7);
        if(!AppsEndpoint.packageName(pkg) || session.getMethod()!=Method.POST
                || !"application/json".equals(session.getHeaders().get("content-type")))return error(Response.Status.BAD_REQUEST,"invalid_request");
        try {
            String length=session.getHeaders().get("content-length");
            if(length==null || !length.matches("[0-9]{1,4}"))throw new IllegalArgumentException();
            int size=Integer.parseInt(length);if(size<1 || size>1024)throw new IllegalArgumentException();
            byte[] bytes=new byte[size];int offset=0,n;
            while(offset<size){n=session.getInputStream().read(bytes,offset,size-offset);if(n<0)throw new IOException();offset+=n;}
            String body=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            ActionMessage.Reader reader=new ActionMessage.Reader(body);
            java.util.Map<String,Object> fields=reader.object(0);reader.space();
            if(reader.pos!=body.length() || fields.size()!=2 || !(fields.get("id") instanceof String)
                    || !((String)fields.get("id")).matches("[A-Za-z0-9_-]{1,64}") || !(fields.get("revision") instanceof Long))
                throw new IllegalArgumentException();
            String id=(String)fields.get("id"), authorization=session.getHeaders().get("authorization");
            String result=launches.launch(phone,pkg,id,(Long)fields.get("revision"),
                    () -> phone.equals(AuthFilter.phone(phones,authorization)));
            if("ok".equals(result))return json(Response.Status.OK,"{\"apiVersion\":1,\"id\":\""+id+"\",\"accepted\":true}");
            return error("unauthorized".equals(result)?Response.Status.UNAUTHORIZED:
                    "not_found".equals(result)?Response.Status.NOT_FOUND:Response.Status.CONFLICT,result);
        }catch(IOException|IllegalArgumentException bad){return error(Response.Status.BAD_REQUEST,"invalid_request");}
    }
    private Response rows(IHTTPSession session,String phone) {
        RowsEndpoint endpoint=bridge.rows;
        if(endpoint==null)return error(Response.Status.SERVICE_UNAVAILABLE,"catalog_unavailable");
        if(session.getMethod()==Method.GET && emptyBody(session)) {
            RowsEndpoint.State value=endpoint.snapshot();Response response=json(Response.Status.OK,value.json());
            response.addHeader("ETag","\""+value.revision+"\"");return response;
        }
        if(session.getMethod()!=Method.PUT || !"application/json".equals(session.getHeaders().get("content-type")))
            return error(Response.Status.BAD_REQUEST,"invalid_request");
        try {
            String length=session.getHeaders().get("content-length");
            if(length==null || !length.matches("[0-9]{1,4}"))throw new IllegalArgumentException();
            int size=Integer.parseInt(length);if(size<1 || size>8192)throw new IllegalArgumentException();
            byte[] bytes=new byte[size];int offset=0,n;
            while(offset<size){n=session.getInputStream().read(bytes,offset,size-offset);if(n<0)throw new IOException();offset+=n;}
            java.nio.charset.CharsetDecoder decoder=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            String body=decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            String auth=session.getHeaders().get("authorization");AppsEndpoint apps=bridge.apps;
            synchronized(endpoint) {
            String result=endpoint.replace(body,session.getHeaders().get("if-match"),apps,
                    () -> phone.equals(AuthFilter.phone(phones,auth)) && apps==bridge.apps);
            if(result!=null)return error("unauthorized".equals(result)?Response.Status.UNAUTHORIZED:
                    "invalid_request".equals(result)?Response.Status.BAD_REQUEST:
                    "not_found".equals(result)?Response.Status.NOT_FOUND:
                    "storage_unavailable".equals(result)?Response.Status.SERVICE_UNAVAILABLE:Response.Status.CONFLICT,result);
            bridge.rowsChanged();return json(Response.Status.OK,endpoint.snapshot().json());
            }
        }catch(IOException|IllegalArgumentException invalid){return error(Response.Status.BAD_REQUEST,"invalid_request");}
    }
    private Response hidden(IHTTPSession session,String phone) {
        HiddenEndpoint endpoint=bridge.hidden;
        if(endpoint==null)return error(Response.Status.SERVICE_UNAVAILABLE,"catalog_unavailable");
        if(session.getMethod()==Method.GET && emptyBody(session)){
            if("/v1/hidden".equals(session.getUri()))return json(Response.Status.OK,endpoint.snapshot().json());
            AppsEndpoint catalog=bridge.management;
            if("/v1/hidden/apps".equals(session.getUri()) && catalog!=null){
                String query=session.getQueryParameterString(),cursor=null;
                if(query!=null){if(!query.matches("cursor=[0-9]{1,15}_[0-9]{1,2}"))return error(Response.Status.BAD_REQUEST,"invalid_request");cursor=query.substring(7);}
                try{return json(Response.Status.OK,catalog.page(cursor,25,endpoint.snapshot().packages));}
                catch(IllegalArgumentException|IllegalStateException bad){return error(Response.Status.CONFLICT,"conflict");}
            }
        }
        if(!"/v1/hidden".equals(session.getUri())||session.getMethod()!=Method.PUT||!"application/json".equals(session.getHeaders().get("content-type")))return error(Response.Status.BAD_REQUEST,"invalid_request");
        try{
            String length=session.getHeaders().get("content-length");if(length==null||!length.matches("[0-9]{1,4}"))throw new IllegalArgumentException();
            int size=Integer.parseInt(length);if(size<1||size>8192)throw new IllegalArgumentException();
            byte[] bytes=new byte[size];int offset=0,n;
            while(offset<size){n=session.getInputStream().read(bytes,offset,size-offset);if(n<0)throw new IOException();offset+=n;}
            String body=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            AppsEndpoint management=bridge.management;String auth=session.getHeaders().get("authorization");
            synchronized(endpoint){
                String result=endpoint.replace(body,session.getHeaders().get("if-match"),management,()->phone.equals(AuthFilter.phone(phones,auth))&&management==bridge.management);
                if(result!=null)return error("unauthorized".equals(result)?Response.Status.UNAUTHORIZED:"invalid_request".equals(result)?Response.Status.BAD_REQUEST:"not_found".equals(result)?Response.Status.NOT_FOUND:"storage_unavailable".equals(result)?Response.Status.SERVICE_UNAVAILABLE:Response.Status.CONFLICT,result);
                bridge.apps=management.visible(endpoint.snapshot().packages);
                bridge.rowsChanged();return json(Response.Status.OK,endpoint.snapshot().json());
            }
        }catch(IOException|IllegalArgumentException bad){return error(Response.Status.BAD_REQUEST,"invalid_request");}
    }
    private Response artwork(IHTTPSession session,String phone){
        String path=session.getUri();
        if(!path.matches("/v1/artwork/[A-Za-z0-9_.]{1,160}/(tile|backdrop)(/commit)?"))return error(Response.Status.BAD_REQUEST,"invalid_request");
        String[] parts=path.split("/");String pkg=parts[3],kind=parts[4];
        AppsEndpoint catalog=bridge.management;MediaJobs jobs=bridge.artwork;
        if(catalog==null||jobs==null)return error(Response.Status.SERVICE_UNAVAILABLE,"busy");
        if(!catalog.contains(pkg))return error(Response.Status.NOT_FOUND,"not_found");
        if(!jobs.acquire())return error(Response.Status.SERVICE_UNAVAILABLE,"busy");
        String authorization=session.getHeaders().get("authorization");
        MediaJobs.Guard guard=()->phone.equals(AuthFilter.phone(phones,authorization))&&bridge.management==catalog;
        try{
            if(session.getMethod()==Method.PUT && parts.length==5){
                String type=session.getHeaders().get("content-type"),length=session.getHeaders().get("content-length");
                if(!("image/png".equals(type)||"image/jpeg".equals(type))||length==null||!length.matches("[0-9]{1,7}"))throw new IllegalArgumentException();
                int size=Integer.parseInt(length);if(size<8||size>UploadValidator.MAX_BYTES)throw new IllegalArgumentException();
                int x=Integer.parseInt(session.getHeaders().containsKey("x-crop-x")?session.getHeaders().get("x-crop-x"):"500"),y=Integer.parseInt(session.getHeaders().containsKey("x-crop-y")?session.getHeaders().get("x-crop-y"):"500");
                String id=jobs.stage(phone,pkg,kind,session.getInputStream(),size,x,y,guard);
                return json(Response.Status.OK,"{\"apiVersion\":1,\"uploadId\":\""+id+"\"}");
            }
            if(session.getMethod()==Method.POST && parts.length==6 && emptyBody(session))jobs.commit(phone,pkg,kind,session.getHeaders().get("x-upload-id"),guard);
            else if(session.getMethod()==Method.DELETE && parts.length==5 && emptyBody(session))jobs.reset(pkg,kind,guard);
            else throw new IllegalArgumentException();
            return json(Response.Status.OK,"{\"apiVersion\":1,\"accepted\":true}");
        }catch(IOException|IllegalArgumentException bad){return error(Response.Status.BAD_REQUEST,"invalid_request");}
        finally{jobs.release();}
    }
    private boolean emptyBody(IHTTPSession session){return !session.getHeaders().containsKey("content-length") || "0".equals(session.getHeaders().get("content-length"));}
    private Response upgrade(IHTTPSession session,String phone){
        String key=session.getHeaders().get("sec-websocket-key");
        if(session.getMethod()!=Method.GET || !emptyBody(session) || !isWebsocketRequested(session)
                || !"13".equals(session.getHeaders().get("sec-websocket-version")) || key==null
                || !key.matches("[A-Za-z0-9+/]{21}[AQgw]==") || session.getHeaders().containsKey("sec-websocket-protocol"))
            return error(Response.Status.BAD_REQUEST,"invalid_request");
        Connection c=connection.get();
        EventHub.Sink sink=new EventHub.Sink(){
            @Override public void send(String text)throws IOException {cSocket(c).send(text);}
            @Override public void close(){abort(c.socket);}
        };
        synchronized(phones){
            // Revocation may race between serve() authentication and reserving an upgraded peer.
            // Use the same store monitor as revokeAll(): it must see and close every accepted peer.
            if(!phone.equals(phones.authenticate(session.getHeaders().get("authorization").substring(7))))
                return error(Response.Status.UNAUTHORIZED,"unauthorized");
            c.peer=events.reserve(phone,sink);
        }
        if(c.peer==null)return error(Response.Status.SERVICE_UNAVAILABLE,"busy");
        EventHub.Peer p=c.peer;
        try {
            c.input.upgrade();c.socket.setSoTimeout(0);c.socket.setSoLinger(true,0);
            Response result=super.serve(session);
            if(result.getStatus()!=Response.Status.SWITCH_PROTOCOL){events.remove(p);return result;}
            c.permit=false;httpSlots.release();return result;
        }catch(IOException|RuntimeException failed){events.remove(p);return error(Response.Status.BAD_REQUEST,"invalid_request");}
    }
    private final java.util.Map<Connection,BoundedSocket> sockets=new java.util.concurrent.ConcurrentHashMap<>();
    private BoundedSocket cSocket(Connection c)throws IOException {BoundedSocket s=sockets.get(c);if(s==null)throw new IOException("Closed");return s;}
    private static void abort(Socket socket){try{socket.setSoLinger(true,0);socket.close();}catch(IOException ignored){}}
    @Override protected WebSocket openWebSocket(IHTTPSession handshake){
        Connection c=connection.get();BoundedSocket socket=new BoundedSocket(handshake,c);sockets.put(c,socket);return socket;
    }
    private final class BoundedSocket extends WebSocket {
        private final Connection c;private final FrameDeadlineInput frames;
        private long second;private int count;
        BoundedSocket(IHTTPSession session,Connection c){this(session,c,new FrameDeadlineInput(session.getInputStream(),events,() -> abort(c.socket)));}
        BoundedSocket(IHTTPSession session,Connection c,FrameDeadlineInput frames){super(session,frames);this.c=c;this.frames=frames;}
        @Override protected void onOpen(){events.activate(c.peer);}
        @Override protected void onFrameRead(){frames.complete();long now=System.nanoTime()/1000000000L;if(now!=second){second=now;count=0;}if(++count>40)events.remove(c.peer);}
        @Override protected void onMessage(WebSocketFrame frame){events.receive(c.peer,frame.getTextPayload());}
        @Override protected void onPong(WebSocketFrame frame){}
        @Override protected void onException(IOException error){}
        @Override protected void onClose(CloseCode code,String reason,boolean remote){frames.complete();sockets.remove(c);events.remove(c.peer);}
        @Override public synchronized void sendFrame(WebSocketFrame frame)throws IOException {
            ScheduledFuture<?> timeout=events.deadline(() -> abort(c.socket),1500);
            try{super.sendFrame(frame);}finally{if(timeout!=null)timeout.cancel(false);}
        }
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
