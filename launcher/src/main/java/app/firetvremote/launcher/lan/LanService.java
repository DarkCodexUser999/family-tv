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
import java.util.Map;
import java.util.LinkedHashMap;

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
    private static final LanControl control = new LanControl();
    private static Revocations revocations;
    private final Runnable networkReconcile = this::reconcile;
    public static volatile String details = "Remote access is OFF.";
    private final BroadcastReceiver networkChange = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { control.connectivity(networkReconcile); }
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
        else recoverRevocation(context.getApplicationContext(), owner);
    }
    private static void start(Context context, Intent intent) {
        try {
            if (context.startService(intent) == null) throw new IllegalStateException("Service unavailable");
        }
        catch (RuntimeException error) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            details = "Remote access could not start on this system.";
            Log.w("FamilyTVLan", "Service start rejected", error);
        }
    }
    @Override public void onCreate() {
        super.onCreate();
        LauncherBridge.SHARED.artwork=app.firetvremote.launcher.ArtworkUploads.create(getApplicationContext());
        LauncherBridge.SHARED.provider=new ProviderSettingsEndpoint(new ProviderSettingsEndpoint.Store(){
            public boolean enabled(){return getSharedPreferences("phone-artwork",MODE_PRIVATE).getBoolean("enabled",false);}
            public boolean save(boolean value){return getSharedPreferences("phone-artwork",MODE_PRIVATE).edit().putBoolean("enabled",value).commit();}
        });
        synchronized (LanService.class) { owner = this; }
        recoverRevocation(getApplicationContext(), this);
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(networkChange, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(networkChange, filter);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        control.execute(this::reconcile);
        return START_NOT_STICKY;
    }

    private void reconcile() {
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
            if (phones == null) {
                phones = loadPhones(getApplicationContext());
                pairing = new PairingWindow(phones, android.os.SystemClock::elapsedRealtime,
                        new java.security.SecureRandom());
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
    }

    private static PairingStore loadPhones(Context context) {
        SharedPreferences saved = context.getSharedPreferences("lan-phone-hashes", MODE_PRIVATE);
        return new PairingStore(new PairingStore.Disk() {
            @Override public Map<String,String> load() {
                Map<String,String> result = new LinkedHashMap<>();
                for (Map.Entry<String,?> entry : saved.getAll().entrySet()) {
                    if (entry.getValue() instanceof String) result.put(entry.getKey(), (String) entry.getValue());
                }
                return result;
            }
            @Override public boolean save(Map<String,String> hashes) {
                SharedPreferences.Editor editor = saved.edit().clear();
                for (Map.Entry<String,String> entry : hashes.entrySet()) editor.putString(entry.getKey(), entry.getValue());
                return editor.commit();
            }
        });
    }

    private static synchronized Revocations journal(Context context) {
        if (revocations == null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
            revocations = new Revocations(new Revocations.Disk() {
                public String read() { return prefs.getString("revocation", ""); }
                public boolean write(String value) { return prefs.edit().putString("revocation", value).commit(); }
            });
        }
        return revocations;
    }

    private static void recoverRevocation(Context app, LanService live) {
        String ticket = journal(app).pending();
        if (ticket != null) enqueueRevocation(app, live, ticket);
    }

    private static void enqueueRevocation(Context app, LanService live, String ticket) {
        Revocations log = journal(app);
        control.execute(() -> {
            if (!ticket.equals(log.pending())) return; // A queued request completed before replay.
            boolean recorded = log.complete(ticket, () -> {
                if (live != null && live.pairing != null) live.pairing.close();
                PairingStore store = live != null && live.phones != null ? live.phones : loadPhones(app);
                return store.revokeAll();
            });
            notifyUser(recorded ? log.status() : "Could not record the revocation outcome. Retry from settings.");
        });
    }

    public static String connectionDetails(Context context) {
        LanService live = owner;
        Discovery d = live == null ? null : live.discovery;
        String result = journal(context).status();
        String pair = context.getSharedPreferences(PREFS, MODE_PRIVATE).getString("pair-result", "");
        return details + (d == null ? "" : "\n\n" + d.status)
                + (pair.isEmpty() ? "" : "\n\n" + pair) + (result.isEmpty() ? "" : "\n\n" + result);
    }

    public static void action(Context context, String action) {
        Context app = context.getApplicationContext();
        LanService live = owner;
        if ("revoke".equals(action)) {
            Revocations log = journal(app);
            String ticket;
            try { ticket = log.begin(); }
            catch (RuntimeException failure) {
                notifyUser("Could not save the revocation request. Retry from settings.");
                return;
            }
            if (ticket != null) enqueueRevocation(app, live, ticket);
            else {
                recoverRevocation(app, live);
                notifyUser("A phone revocation is pending. Check connection details for its outcome.");
            }
        } else if ("pair".equals(action)) {
            if (live == null || !enabled(app)) pairResult(app, "Enable remote access and wait for its address first.");
            else {
                rememberPairResult(app, "Pairing was requested. Select Pair a phone again if you missed the PIN.");
                if (!control.offerPair(() -> live.openPairing(app))) pairResult(app, "Remote access is busy. Retry pairing.");
            }
        }
    }

    private void openPairing(Context app) {
        if (destroyed || !enabled(app) || pairing == null || server == null) {
            pairResult(app, "Remote access is unavailable. Retry pairing when its address appears.");
            return;
        }
        try {
            String pin = pairing.open();
            String message = "PIN: " + pin + "\nValid for 2 minutes, one phone only.\n"
                    + "Confirm the certificate fingerprint in connection details.\nPairing session: " + pairing.sessionId();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Notice target = notice;
                if (owner == this && !destroyed && enabled(app) && target != null) target.show(message);
            });
        } catch (RuntimeException failure) {
            pairing.close();
            pairResult(app, "Could not open pairing. Retry from settings.");
        }
    }

    private static void rememberPairResult(Context app, String message) {
        app.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("pair-result", message).apply();
    }

    private static void pairResult(Context app, String message) {
        rememberPairResult(app, message);
        notifyUser(message);
    }

    private static void notifyUser(String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            Notice target = notice;
            if (target != null) target.show(message);
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
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
