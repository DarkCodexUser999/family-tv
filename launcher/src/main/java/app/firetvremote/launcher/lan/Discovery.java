package app.firetvremote.launcher.lan;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

/** Asynchronous, event-only NSD. Failure never disables the manual HTTPS address. */
final class Discovery implements NsdManager.RegistrationListener {
    private final NsdManager manager;
    private boolean stopped, registered;
    volatile String status="Discovery is starting; manual address remains available.";
    Discovery(Context context, int port, String id) {
        manager=(NsdManager)context.getSystemService(Context.NSD_SERVICE);
        NsdServiceInfo info=new NsdServiceInfo();
        info.setServiceName("Family TV "+id.substring(0,8)); info.setServiceType("_familytv._tcp."); info.setPort(port);
        info.setAttribute("api","1"); // Never publish PINs, session IDs, tokens or certificate trust assertions.
        try { manager.registerService(info,NsdManager.PROTOCOL_DNS_SD,this); }
        catch(RuntimeException unavailable) { status="Discovery unavailable; use the manual address."; }
    }
    synchronized void stop() {
        stopped=true;
        if(registered) unregister(); // Pending registration is removed in its callback, rather than leaked.
    }
    private void unregister() {
        try { manager.unregisterService(this); registered=false; }
        catch(RuntimeException unavailable) { status="Discovery cleanup could not be confirmed."; }
    }
    @Override public synchronized void onServiceRegistered(NsdServiceInfo info) {
        registered=true;
        if(stopped) unregister(); else status="Discoverable as "+info.getServiceName();
    }
    @Override public synchronized void onRegistrationFailed(NsdServiceInfo info,int code) {
        registered=false; status="Discovery unavailable; use the manual address.";
    }
    @Override public synchronized void onServiceUnregistered(NsdServiceInfo info) { registered=false; }
    @Override public synchronized void onUnregistrationFailed(NsdServiceInfo info,int code) {
        status="Discovery cleanup could not be confirmed; switch remote access OFF.";
    }
}
