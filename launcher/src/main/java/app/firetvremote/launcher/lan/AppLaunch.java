package app.firetvremote.launcher.lan;

import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One expiring UI request, no automatic retry. Accepted means the existing tile click was dispatched. */
final class AppLaunch {
    interface Guard { boolean authorized(); }
    private final LauncherBridge bridge;
    private final AtomicBoolean pending=new AtomicBoolean();
    private final LinkedHashMap<String,String> seen=new LinkedHashMap<>();
    AppLaunch(LauncherBridge bridge){this.bridge=bridge;}
    String launch(String phone,String pkg,String id,long revision,Guard authorized) {
        String key=phone+":"+id, value=pkg+":"+revision;
        synchronized(seen){if(seen.containsKey(key))return !value.equals(seen.get(key))?"invalid_request":bridge.apps==null||!bridge.apps.contains(pkg)?"not_found":"ok";}
        AppsEndpoint snapshot=bridge.apps;
        if(snapshot==null || snapshot.revision!=revision)return "stale_catalog";
        if(!snapshot.contains(pkg))return "not_found";
        LauncherBridge.Ui ui=bridge.ui();long epoch=bridge.epoch(), start=System.nanoTime();
        if(ui==null || !bridge.canNavigate())return "ui_unavailable";
        if(!pending.compareAndSet(false,true))return "busy";
        CountDownLatch done=new CountDownLatch(1);AtomicBoolean canceled=new AtomicBoolean();
        String[] result={"expired"};
        try {
            ui.post(() -> {
                try {
                    synchronized(canceled) {
                        if(canceled.get() || System.nanoTime()-start>500000000L)return;
                        if(!authorized.authorized()){result[0]="unauthorized";return;}
                        if(bridge.ui()!=ui || bridge.epoch()!=epoch || !bridge.canNavigate() || bridge.apps!=snapshot){result[0]="ui_unavailable";return;}
                        result[0]=ui.launch(pkg)?"ok":"ui_unavailable";
                        if("ok".equals(result[0]))synchronized(seen){
                            if(seen.size()>=64)seen.remove(seen.keySet().iterator().next());seen.put(key,value);
                        }
                    }
                }catch(RuntimeException failed){result[0]="ui_unavailable";}
                finally{done.countDown();}
            });
            done.await(600,TimeUnit.MILLISECONDS);
            synchronized(canceled){canceled.set(true);return result[0];}
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return "expired";}
        finally{synchronized(canceled){canceled.set(true);}pending.set(false);}
    }
}
