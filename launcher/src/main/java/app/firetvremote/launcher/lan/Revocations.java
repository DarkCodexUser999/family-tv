package app.firetvremote.launcher.lan;

import java.util.UUID;

/** One outstanding request. Only the serial control worker invalidates credentials. */
final class Revocations {
    interface Disk { String read(); boolean write(String value); }
    interface Revoke { boolean run(); }
    static final String DONE = "All phone access has been revoked.";
    static final String FAILED = "Phone revocation failed. Retry from Family TV settings.";
    private static final String PREFIX = "pending/";
    private final Disk disk;
    Revocations(Disk disk) { this.disk = disk; }

    synchronized String begin() {
        if (pending() != null) return null;
        String ticket = PREFIX + UUID.randomUUID();
        String previous = disk.read();
        if (!disk.write(ticket)) {
            disk.write(previous);
            throw new IllegalStateException("Could not record revocation request");
        }
        return ticket;
    }

    synchronized String pending() {
        String value = disk.read();
        return value.startsWith(PREFIX) ? value : null;
    }

    synchronized String status() {
        return pending() == null ? disk.read() : "Phone revocation is pending.";
    }

    boolean complete(String ticket, Revoke action) {
        if (!ticket.equals(pending())) return false;
        boolean revoked;
        try { revoked = action.run(); }
        catch (RuntimeException failure) { revoked = false; }
        synchronized (this) {
            if (disk.write(revoked ? DONE : FAILED)) return true;
            // SharedPreferences can change its in-memory value even when commit fails.
            disk.write(ticket);
            return false;
        }
    }
}
