package app.firetvremote.launcher.lan;

import java.io.IOException;

/** Write-only opt-in. Keys belong to each phone, never this endpoint. */
public final class ProviderSettingsEndpoint {
    public interface Store { boolean enabled(); boolean save(boolean enabled); }
    private final Store store;
    public ProviderSettingsEndpoint(Store store) { this.store=store; }
    public synchronized boolean enabled() { return store.enabled(); }
    public synchronized void set(boolean value) throws IOException {
        if(!store.save(value))throw new IOException("storage_unavailable");
    }
}
