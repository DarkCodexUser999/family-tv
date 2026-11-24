package app.firetvremote.launcher.lan;
import java.util.*;
import java.util.concurrent.*;
public final class AppLaunchTest {
    static int checks;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static final class Ui implements LauncherBridge.Ui {
        final BlockingQueue<Runnable> tasks=new LinkedBlockingQueue<>();int opened;
        public void post(Runnable r){tasks.add(r);}
        public boolean perform(String action){return true;}
        public boolean launch(String pkg){opened++;return true;}
    }
    public static void main(String[] args)throws Exception {
        LauncherBridge b=new LauncherBridge();Ui ui=new Ui();b.resume(ui);
        b.apps=new AppsEndpoint(10,Arrays.asList(new AppsEndpoint.App("tv.youtube","YouTube",null)));
        AppLaunch launch=new AppLaunch(b);ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            check("not_found".equals(launch.launch("phone","tv.hidden","a",10,()->true)),"hidden rejected");
            check("stale_catalog".equals(launch.launch("phone","tv.youtube","b",9,()->true)),"revision required");
            Future<String> revoke=worker.submit(()->launch.launch("phone","tv.youtube","c",10,()->false));
            ui.tasks.poll(1,TimeUnit.SECONDS).run();check("unauthorized".equals(revoke.get())&&ui.opened==0,"queued revocation");
            Future<String> paused=worker.submit(()->launch.launch("phone","tv.youtube","d",10,()->true));
            Runnable queued=ui.tasks.poll(1,TimeUnit.SECONDS);b.pause(ui);b.resume(ui);queued.run();
            check("ui_unavailable".equals(paused.get())&&ui.opened==0,"lifecycle cancels queued launch");
            Future<String> expired=worker.submit(()->launch.launch("phone","tv.youtube","e",10,()->true));
            queued=ui.tasks.poll(1,TimeUnit.SECONDS);
            check("busy".equals(launch.launch("other","tv.youtube","f",10,()->true)),"one in-flight launch");
            check("expired".equals(expired.get(2,TimeUnit.SECONDS)),"blocked UI expires");queued.run();
            check(ui.opened==0,"expired launch cannot execute later");
            Future<String> good=worker.submit(()->launch.launch("phone","tv.youtube","g",10,()->true));
            ui.tasks.poll(1,TimeUnit.SECONDS).run();check("ok".equals(good.get())&&ui.opened==1,"one dispatch");
            check("ok".equals(launch.launch("phone","tv.youtube","g",10,()->true))&&ui.opened==1,"idempotent retry");
            check("invalid_request".equals(launch.launch("phone","tv.other","g",10,()->true)),"ID bound to target");
        }finally{worker.shutdownNow();}
        System.out.println("PASS: "+checks+" explicit launch expiry, revocation, lifecycle and replay checks");
    }
}
