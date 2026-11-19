package app.firetvremote.launcher.lan;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Enabled only by an explicit launcher setting; event-driven, no polling or boot receiver. */
public final class LanService extends Service {
    private static volatile LanService owner;
    public interface Notice { void show(String message); }
    public static volatile Notice notice;
    private static final String PREFS = "lan-access";
    private volatile boolean destroyed;
    private Transport server;
    private volatile Discovery discovery;
    private PairingStore phones;
    private PairingWindow pairing;
    private String address;
    private ThreadPoolExecutor control;
    public static volatile String details = "Remote access is OFF.";
    private final BroadcastReceiver networkChange = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { sync(); }
    };
    public static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", false);
    }
    public static synchronized void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply();
        Intent intent = new Intent(context, LanService.class);
        if (enabled) {
            details = "Starting secure remote access…";
            start(context, intent);
        } else { details = "Remote access is OFF."; context.stopService(intent); }
    }
    public static void resumeIfEnabled(Context context) {
        if (enabled(context)) start(context, new Intent(context, LanService.class));
    }
    private static void start(Context context, Intent intent) {
        try { context.startService(intent); }
        catch (RuntimeException error) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            details = "Remote access could not start on this system.";
            Log.w("FamilyTVLan", "Service start rejected", error);
        }
    }
    @Override public void onCreate() {
        super.onCreate();
        synchronized (LanService.class) { owner = this; }
        control = new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(1), task -> new Thread(task, "family-tv-lan-state"),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(networkChange, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(networkChange, filter);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        if(intent!=null && "pair".equals(intent.getAction())) control.execute(() -> {
            if(pairing==null || server==null || !enabled(this)) {
                notifyUser("Enable remote access and wait for its address first."); return;
            }
            String pin=pairing.open();
            String message="PIN: "+pin+"\nValid for 2 minutes, one phone only.\n"
                    +"Confirm the certificate fingerprint in connection details.\n"
                    +"Pairing session: "+pairing.sessionId();
            notifyUser(message);
        });
        else if(intent!=null && "revoke".equals(intent.getAction())) control.execute(() -> {
            if(pairing!=null) pairing.close();
            notifyUser(phones!=null && phones.revokeAll()?"All phone access has been revoked.":"Revocation failed or server is OFF. Retry with remote access ON.");
        });
        else sync();
        return START_NOT_STICKY;
    }
    private void sync() {
        if (!destroyed) control.execute(() -> {
            if (destroyed || !enabled(this)) { closeServer(); return; }
            try {
                InetAddress next = wifiAddress();
                if (next == null) { closeServer(); publish("Waiting for private Wi-Fi."); return; }
                if (server != null && next.getHostAddress().equals(address)) return;
                closeServer();
                publish("Starting secure remote access…");
                TlsIdentity identity = new TlsIdentity(this);
                if (destroyed || !enabled(this)) return;
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                String id = prefs.getString("installation-id", null);
                if (id == null) { id = UUID.randomUUID().toString(); prefs.edit().putString("installation-id", id).apply(); }
                if(phones==null) {
                    SharedPreferences saved=getSharedPreferences("lan-phone-hashes",MODE_PRIVATE);
                    phones=new PairingStore(new PairingStore.Disk() {
                        @Override public java.util.Map<String,String> load() {
                            java.util.Map<String,String> result=new java.util.LinkedHashMap<>();
                            for(java.util.Map.Entry<String,?> e:saved.getAll().entrySet())
                                if(e.getValue() instanceof String) result.put(e.getKey(),(String)e.getValue());
                            return result;
                        }
                        @Override public boolean save(java.util.Map<String,String> hashes) {
                            SharedPreferences.Editor editor=saved.edit().clear();
                            for(java.util.Map.Entry<String,String> e:hashes.entrySet()) editor.putString(e.getKey(),e.getValue());
                            return editor.commit(); // This is the LAN worker, never the focus/UI thread.
                        }
                    });
                    pairing=new PairingWindow(phones,android.os.SystemClock::elapsedRealtime,new java.security.SecureRandom());
                }
                Transport nextServer = new Transport(next, identity.sockets, id, phones, pairing, LauncherBridge.SHARED);
                nextServer.open();
                server = nextServer;
                address = next.getHostAddress();
                discovery=new Discovery(this,server.getListeningPort(),id);
                if (destroyed || !enabled(this)) { closeServer(); return; }
                publish("Address: https://" + address + ":" + server.getListeningPort()
                        + "/v1/info\n\nCertificate SHA-256:\n" + identity.fingerprint
                        + "\n\nPair a phone using Family TV settings. Paired phones can navigate Family TV while it is open. Discovery failure: use this manual address.");
            } catch (Exception error) {
                closeServer();
                publish("Secure remote access could not start. Switch OFF and retry.");
                Log.w("FamilyTVLan", "Secure transport unavailable", error);
            }
        });
    }
    public static String connectionDetails() {
        LanService live=owner; Discovery d=live==null?null:live.discovery;
        return details+(d==null?"":"\n\n"+d.status);
    }
    public static void action(Context context,String action) {
        if(enabled(context)) start(context,new Intent(context,LanService.class).setAction(action));
    }
    private void notifyUser(String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            Notice target=notice;
            if(owner==this && !destroyed && enabled(this) && target!=null) target.show(message);
        });
    }
    private void publish(String value) {
        synchronized (LanService.class) {
            if (owner == this && !destroyed && enabled(this)) details = value;
        }
    }
    private InetAddress wifiAddress() throws Exception {
        ConnectivityManager connectivity = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo active = connectivity.getActiveNetworkInfo();
        if (active == null || !active.isConnected() || active.getType() != ConnectivityManager.TYPE_WIFI) return null;
        WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        int ip = wifi.getConnectionInfo().getIpAddress();
        InetAddress result = InetAddress.getByAddress(new byte[] {(byte)ip, (byte)(ip >> 8), (byte)(ip >> 16), (byte)(ip >> 24)});
        return ServerLimits.privateV4(result) ? result : null;
    }
    private void closeServer() {
        if(pairing!=null) pairing.close();
        if(discovery!=null) { discovery.stop(); discovery=null; }
        if (server != null) { server.stop(); server = null; address = null; }
    }
    @Override public void onDestroy() {
        destroyed = true;
        synchronized (LanService.class) {
            if (owner == this) { owner = null; details = "Remote access is OFF."; }
        }
        unregisterReceiver(networkChange);
        control.execute(this::closeServer);
        control.shutdown();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
