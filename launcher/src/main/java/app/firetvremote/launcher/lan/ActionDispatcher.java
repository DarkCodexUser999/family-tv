package app.firetvremote.launcher.lan;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** One bounded main-thread drain; timestamps are in the server's monotonic clock domain. */
public final class ActionDispatcher {
    public interface Clock { long now(); }
    public interface Client { boolean alive(); void result(String id,String error); }
    private static final class Seen { long at; String action,result; Seen(long at,String action){this.at=at;this.action=action;} }
    private static final class Phone {
        final LinkedHashMap<String,Seen> ids=new LinkedHashMap<>(); long second=-1;int count;
    }
    private static final class Pending {
        final ActionMessage message;final Client client;final Seen seen;
        Pending(ActionMessage m,Client c,Seen s){message=m;client=c;seen=s;}
    }
    private final LauncherBridge bridge; private final Clock clock;
    private final Map<String,Phone> phones=new LinkedHashMap<>();
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private boolean scheduled,closed; private long second=-1;private int count;
    private final Runnable drain=this::drain;
    public ActionDispatcher(LauncherBridge bridge,Clock clock){this.bridge=bridge;this.clock=clock;}
    public synchronized void receive(String phone,ActionMessage m,Client client) {
        long now=clock.now();
        if(closed || !client.alive()){client.result(m.id,"ui_unavailable");return;}
        Phone rate=phones.get(phone);
        if(rate==null){if(phones.size()>=8){client.result(m.id,"busy");return;}rate=new Phone();phones.put(phone,rate);}
        Seen old=rate.ids.get(m.id);
        if(old!=null && now-old.at<10000) {client.result(m.id, !old.action.equals(m.action)?"invalid_request":old.result==null?"busy":old.result);return;}
        if(m.sentAt>now+50 || now-m.sentAt>500){client.result(m.id,"expired");return;}
        if(m.epoch!=bridge.epoch() || !bridge.canNavigate()){client.result(m.id,"ui_unavailable");return;}
        if(rate.second!=now/1000){rate.second=now/1000;rate.count=0;}
        if(second!=now/1000){second=now/1000;count=0;}
        if(rate.count>=20 || count>=30){client.result(m.id,"rate_limited");return;}
        if(pending.size()>=8){client.result(m.id,"busy");return;}
        rate.count++;count++;
        if(rate.ids.size()>=64)rate.ids.remove(rate.ids.keySet().iterator().next());
        Seen seen=new Seen(now,m.action);rate.ids.put(m.id,seen);pending.add(new Pending(m,client,seen));
        if(!scheduled){scheduled=true;LauncherBridge.Ui ui=bridge.ui();if(ui!=null)ui.post(drain);else {scheduled=false;pending.clear();seen.result="ui_unavailable";client.result(m.id,"ui_unavailable");}}
    }
    private void drain() {
        // At most eight operations per posted batch; every operation rechecks lifecycle and session.
        for(int i=0;i<8;i++) {
            Pending task;
            synchronized(this){task=pending.poll();if(task==null){scheduled=false;return;}}
            String result="ok";
            if(closed || !task.client.alive() || task.message.epoch!=bridge.epoch() || !bridge.canNavigate())result="ui_unavailable";
            else if(clock.now()-task.message.sentAt>500)result="expired";
            else {try{if(!bridge.perform(task.message.action))result="ui_unavailable";}catch(RuntimeException failed){result="ui_unavailable";}}
            synchronized(this){task.seen.result=result;}
            task.client.result(task.message.id,result);
        }
        synchronized(this){scheduled=false;if(!pending.isEmpty()){LauncherBridge.Ui ui=bridge.ui();if(ui!=null){scheduled=true;ui.post(drain);}else pending.clear();}}
    }
    public synchronized void invalidate(){pending.clear();phones.clear();}
    public synchronized void close(){closed=true;pending.clear();phones.clear();}
    synchronized int queued(){return pending.size();}
}
