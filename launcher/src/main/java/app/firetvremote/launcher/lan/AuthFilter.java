package app.firetvremote.launcher.lan;

/** Shared gate for HTTP and any future WebSocket upgrade; tokens never come from URLs. */
final class AuthFilter {
    static String phone(PairingStore store, String header) {
        if(store==null || header==null || !header.startsWith("Bearer ")) return null;
        return store.authenticate(header.substring(7));
    }
}
