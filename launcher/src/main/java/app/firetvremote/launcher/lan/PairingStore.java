package app.firetvremote.launcher.lan;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stores only SHA-256 bearer digests. Persistence must finish before a token is issued. */
public final class PairingStore {
    public interface Disk { Map<String,String> load(); boolean save(Map<String,String> hashes); }
    public volatile Runnable onRevoked;
    private final Disk disk;
    private final Map<String,String> hashes;
    public PairingStore(Disk disk) {
        this.disk = disk; hashes = new LinkedHashMap<>();
        Map<String,String> saved = disk.load();
        if (saved.size() <= 8) for (Map.Entry<String,String> e : saved.entrySet()) {
            if (e.getKey().matches("[0-9a-f]{32}") && e.getValue().matches("[0-9a-f]{64}")) hashes.put(e.getKey(),e.getValue());
        }
    }
    static String digest(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length*2);
        for(byte b:bytes) out.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
        return out.toString();
    }
    synchronized boolean add(String id, String token) {
        if (hashes.size() >= 8 || hashes.containsKey(id)) return false;
        Map<String,String> next = new LinkedHashMap<>(hashes); next.put(id,digest(token));
        if (!disk.save(next)) return false;
        hashes.clear(); hashes.putAll(next); return true;
    }
    public synchronized String authenticate(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) return null;
        byte[] wanted = digest(token).getBytes(StandardCharsets.US_ASCII);
        for(Map.Entry<String,String> e:hashes.entrySet()) {
            if(MessageDigest.isEqual(wanted,e.getValue().getBytes(StandardCharsets.US_ASCII))) return e.getKey();
        }
        return null;
    }
    public synchronized boolean revokeAll() {
        if (!disk.save(new LinkedHashMap<String,String>())) return false;
        hashes.clear(); Runnable listener=onRevoked;if(listener!=null)listener.run();return true;
    }
    public synchronized int count() { return hashes.size(); }
}
