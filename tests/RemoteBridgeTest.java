package app.firetvremote.launcher.lan;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;
import fi.iki.elonen.NanoWSD.WebSocketFrame;

public final class RemoteBridgeTest {
    static int checks;
    static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks++;}
    static class Ui implements LauncherBridge.Ui {
        final java.util.Queue<Runnable> tasks=new java.util.concurrent.ConcurrentLinkedQueue<>();final List<String> actions=new ArrayList<>();
        public void post(Runnable r){tasks.add(r);}public boolean perform(String action){actions.add(action);return true;}
        void drain(){while(!tasks.isEmpty())tasks.poll().run();}
    }
    static class Client implements ActionDispatcher.Client {
        boolean active=true;String last;public boolean alive(){return active;}public void result(String id,String error){last=error;}
    }
    static String message(String id,String action,long sent,long epoch){return "{\"v\":1,\"type\":\"action\",\"id\":\""+id+"\",\"payload\":{\"id\":\""+id+"\",\"action\":\""+action+"\",\"sentAtMs\":"+sent+",\"epoch\":"+epoch+"}}";}
    static ActionMessage cmd(String id,String action,long sent,long epoch){return ActionMessage.parse(message(id,action,sent,epoch));}
    public static void main(String[] args)throws Exception {
        LauncherBridge bridge=new LauncherBridge();Ui ui=new Ui();AtomicLong clock=new AtomicLong(1000);Client client=new Client();
        ActionDispatcher d=new ActionDispatcher(bridge,clock::get);
        check(!bridge.canNavigate(),"inactive by default");bridge.resume(ui);
        long epoch=bridge.epoch();ActionMessage ok=cmd("one","OK",1000,epoch);
        d.receive("phone",ok,client);d.receive("phone",ok,client);check(d.queued()==1,"duplicate OK queued once");ui.drain();
        check(ui.actions.size()==1&&"ok".equals(client.last),"one dispatch and ACK");d.receive("phone",ok,client);ui.drain();check(ui.actions.size()==1,"completed duplicate not dispatched");
        d.receive("phone",cmd("one","BACK",1000,epoch),client);check("invalid_request".equals(client.last),"ID reuse for another action");
        d.receive("phone",cmd("stale","OK",499,epoch),client);check("expired".equals(client.last),"arrival expiry");
        d.receive("phone",cmd("future","OK",1100,epoch),client);check("expired".equals(client.last),"future timestamp");
        d.receive("phone",cmd("queued","RIGHT",1000,epoch),client);clock.set(1501);ui.drain();check("expired".equals(client.last)&&ui.actions.size()==1,"main-queue expiry");
        clock.set(2000);d.receive("phone",cmd("gone","OK",2000,epoch),client);client.active=false;ui.drain();check(ui.actions.size()==1,"disconnect cancels queued OK");client.active=true;
        d.receive("phone",cmd("pause","OK",2000,epoch),client);bridge.pause(ui);bridge.resume(ui);ui.drain();check(ui.actions.size()==1,"pause/resume generation invalidates old input");
        bridge.busy(true);d.receive("phone",cmd("loading","OK",2000,bridge.epoch()),client);check("ui_unavailable".equals(client.last),"loading gates input");bridge.busy(false);
        for(int i=0;i<9;i++)d.receive("phone",cmd("q"+i,"LEFT",2000,bridge.epoch()),client);
        check(d.queued()==8&&"busy".equals(client.last),"bounded main queue");ui.drain();
        for(int i=0;i<30;i++){d.receive("phone",cmd("rate"+i,"UP",2000,bridge.epoch()),client);ui.drain();}
        check("rate_limited".equals(client.last),"phone rate cap");
        for(String bad:new String[]{message("a","AMAZON",1,1),message("a","OK",1,1).replace("\"v\":1","\"v\":1,\"v\":1"),
            message("a","OK",1,1).replace("\"payload\":{\"id\":\"a\"","\"payload\":{\"id\":\"b\""),
            message("a","OK",1,1)+"x",message("a","OK",1,1).replace("\"sentAtMs\":1","\"sentAtMs\":1.0")}) {
            try{ActionMessage.parse(bad);throw new AssertionError("bad command accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        // Patched upstream parser rejects before allocating advertised huge payloads.
        for(byte[] bad:new byte[][]{new byte[]{(byte)0x81,126,0x10,1},new byte[]{1,(byte)0x80,0,0,0,0},new byte[]{(byte)0x82,(byte)0x80,0,0,0,0},
            new byte[]{(byte)0x81,(byte)0xff,0,0,0,1,0,0,0,0},new byte[]{(byte)0x81,0}}) {
            try{WebSocketFrame.read(new ByteArrayInputStream(bad));throw new AssertionError("unsafe frame accepted");}catch(IOException expected){checks++;}
        }
        EventHub hub=new EventHub(bridge,clock::get);
        List<String> sent=Collections.synchronizedList(new ArrayList<>());
        EventHub.Peer peer=hub.reserve("p",new EventHub.Sink(){public void send(String value){sent.add(value);}public void close(){}});
        check(peer!=null&&hub.reserve("p",new EventHub.Sink(){public void send(String s){}public void close(){}})==null,"one connection per phone");
        hub.activate(peer);long deadline=System.nanoTime()+1000000000L;while(sent.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(5);
        check(!sent.isEmpty()&&sent.get(0).contains("\"serverTimeMs\""),"initial state includes monotonic clock");hub.close();check(!peer.alive(),"disable closes sessions");
        // A blocked writer is aborted by a one-shot deadline; a flood cannot grow the reply queue.
        EventHub slow=new EventHub(bridge,clock::get);CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
        EventHub.Peer p=slow.reserve("slow",new EventHub.Sink(){public void send(String value)throws IOException{blocked.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}}public void close(){release.countDown();}});
        slow.activate(p);check(blocked.await(1,TimeUnit.SECONDS),"slow writer entered");
        for(int i=0;i<10;i++)p.result("id"+i,"ok");
        check(!p.alive(),"queue overflow closes slow reader");slow.close();
        EventHub deadlineHub=new EventHub(bridge,clock::get);CountDownLatch waited=new CountDownLatch(1);
        EventHub.Peer timed=deadlineHub.reserve("timed",new EventHub.Sink(){public void send(String v)throws IOException{try{waited.await();}catch(InterruptedException e){throw new IOException(e);}}public void close(){waited.countDown();}});
        deadlineHub.activate(timed);check(waited.await(2500,TimeUnit.MILLISECONDS)&&!timed.alive(),"one-shot blocked-write deadline");deadlineHub.close();
        d.close();System.out.println("PASS: "+checks+" remote bridge, expiry, replay, bounds and slow-reader checks");
    }
}
