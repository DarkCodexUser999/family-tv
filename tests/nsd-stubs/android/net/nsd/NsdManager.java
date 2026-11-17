package android.net.nsd;
public abstract class NsdManager {
    public static final int PROTOCOL_DNS_SD=1;
    public interface RegistrationListener {
        void onServiceRegistered(NsdServiceInfo info); void onRegistrationFailed(NsdServiceInfo info,int code);
        void onServiceUnregistered(NsdServiceInfo info); void onUnregistrationFailed(NsdServiceInfo info,int code);
    }
    public abstract void registerService(NsdServiceInfo info,int protocol,RegistrationListener listener);
    public abstract void unregisterService(RegistrationListener listener);
}
