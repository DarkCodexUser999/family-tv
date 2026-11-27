package app.firetvremote.launcher.lan;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Two peers, <=8 replies each, coalesced state; no writer or timers waking while idle. */
public final class EventHub implements LauncherBridge.Signal {
    public interface Sink { void send(String text) throws IOException; void close(); }
    public final class Peer implements ActionDispatcher.Client {
        final String phone;final Sink sink;final ArrayDeque<String> replies=new ArrayDeque<>();
        volatile boolean closed;boolean ready,dirty=true;
        Peer(String phone,Sink sink){this.phone=phone;this.sink=sink;}
        @Override public boolean alive(){return !closed && !EventHub.this.closed;}
        @Override public void result(String id,String error){
            if("ok".equals(error)) offer("{\"v\":1,\"type\":\"ack\",\"id\":\""+id+"\",\"payload\":{\"apiVersion\":1,\"id\":\""+id+"\",\"accepted\":true}}");
            else offer("{\"v\":1,\"type\":\"error\",\"id\":\""+id+"\",\"payload\":{\"apiVersion\":1,\"requestId\":\""+id+"\",\"error\":{\"code\":\""+error+"\",\"message\":\"Input unavailable\"}}}");
        }
        private void offer(String message){
            boolean overflow;
            synchronized(EventHub.this){if(!alive())return;overflow=replies.size()>=8;
                if(!overflow){replies.add(message);EventHub.this.notifyAll();}}
            if(overflow)remove(this);
        }
        public void malformed(){remove(this);}
    }
    private final java.util.Set<Peer> closing=new java.util.HashSet<>();
    private final Peer[] peers=new Peer[2];private final LauncherBridge bridge;private final ActionDispatcher.Clock clock;
    private final ActionDispatcher dispatcher;private volatile boolean closed;private Thread writer;private long sequence;private int nextPeer;
    private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1, task -> {
        Thread t=new Thread(task,"family-tv-deadlines");t.setDaemon(true);return t;
    });
    public EventHub(LauncherBridge bridge,ActionDispatcher.Clock clock){
        this.bridge=bridge;this.clock=clock;dispatcher=new ActionDispatcher(bridge,clock);
        deadlines.setRemoveOnCancelPolicy(true);deadlines.setKeepAliveTime(15,TimeUnit.SECONDS);deadlines.allowCoreThreadTimeOut(true);

    }
    public synchronized Peer reserve(String phone,Sink sink){
        if(closed)return null;for(Peer p:peers)if(p!=null&&p.phone.equals(phone))return null;
        for(int i=0;i<peers.length;i++)if(peers[i]==null){Peer p=new Peer(phone,sink);peers[i]=p;bridge.listen(this);return p;}return null;
    }
    public synchronized void activate(Peer peer){
        if(!peer.alive())return;peer.ready=true;
        if(writer==null){writer=new Thread(this::writeLoop,"family-tv-events");writer.setDaemon(true);writer.start();}notifyAll();
    }
    @Override public synchronized void changed(){for(Peer p:peers)if(p!=null)p.dirty=true;notifyAll();}
    public void receive(Peer peer,String text){
        if(!peer.alive())return;
        try {dispatcher.receive(peer.phone,ActionMessage.parse(text),peer);}
        catch(IllegalArgumentException bad){remove(peer);}
    }
    public void remove(Peer peer){
        synchronized(this){
            if(peer.closed)return;peer.closed=true;peer.replies.clear();closing.add(peer);
            for(int i=0;i<peers.length;i++)if(peers[i]==peer)peers[i]=null;
            boolean any=false;for(Peer p:peers)if(p!=null)any=true;if(!any)bridge.detach(this);notifyAll();
        }
        // Callers include the UI ACK path: socket close must never run there or under the hub monitor.
        try{deadlines.execute(() -> {try{peer.sink.close();}finally{synchronized(EventHub.this){closing.remove(peer);}}});}
        catch(java.util.concurrent.RejectedExecutionException stopped){/* close() already owns cleanup. */}
    }
    public void revoke(){synchronized(this){for(Peer p:peers)if(p!=null)remove(p);}dispatcher.invalidate();}
    public void close(){
        closed=true;bridge.detach(this);revoke();dispatcher.close();
        Peer[] finish;synchronized(this){finish=closing.toArray(new Peer[0]);closing.clear();}
        for(Peer p:finish)p.sink.close();deadlines.shutdownNow();
    }
    public ScheduledFuture<?> deadline(Runnable close,long millis){
        try{return deadlines.schedule(close,millis,TimeUnit.MILLISECONDS);}catch(java.util.concurrent.RejectedExecutionException stopped){close.run();return null;}
    }
    public String status(){return StatusEndpoint.json(bridge);}
    private String state(){
        // Read epoch twice so a concurrent lifecycle transition cannot advertise an old true flag with a new epoch.
        long before,epoch;boolean foreground,navigate;String focus;
        do{before=bridge.epoch();foreground=bridge.foreground();navigate=bridge.canNavigate();focus=bridge.focused();epoch=bridge.epoch();}while(before!=epoch || (epoch&1)!=0);
        if(focus!=null&&!focus.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))focus=null;
        return "{\"v\":1,\"type\":\"state\",\"id\":\"s"+(++sequence)+"\",\"payload\":{\"foreground\":"+foreground+",\"canNavigate\":"+navigate
            +",\"package\":"+(focus==null?"null":"\""+focus+"\"")+",\"epoch\":"+epoch+",\"serverTimeMs\":"+clock.now()+"}}";
    }
    private void writeLoop(){
        while(true){Peer target=null;String message=null;boolean state=false;
            synchronized(this){
                for(int offset=0;offset<peers.length;offset++){
                    int index=(nextPeer+offset)%peers.length;Peer p=peers[index];
                    if(p==null||!p.ready||!p.alive())continue;
                    if(!p.replies.isEmpty()){target=p;message=p.replies.poll();nextPeer=(index+1)%peers.length;break;}
                    if(p.dirty){target=p;p.dirty=false;state=true;nextPeer=(index+1)%peers.length;break;}
                }
                if(target==null){
                    boolean any=false;for(Peer p:peers)if(p!=null)any=true;
                    if(closed||!any){writer=null;return;}
                    try{wait();}catch(InterruptedException stop){writer=null;return;}continue;
                }
            }
            if(state)message=state();
            final Peer sending=target;
            ScheduledFuture<?> timer=deadline(() -> remove(sending),1500);
            try{if(target.alive())target.sink.send(message);}catch(IOException|RuntimeException disconnected){remove(target);}
            finally{if(timer!=null)timer.cancel(false);}
        }
    }
}
