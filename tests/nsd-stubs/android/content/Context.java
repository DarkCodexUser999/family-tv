package android.content;
/** Host-only fake; excluded from APK source sets. */
public abstract class Context {
    public static final String NSD_SERVICE="servicediscovery";
    public abstract Object getSystemService(String name);
}
