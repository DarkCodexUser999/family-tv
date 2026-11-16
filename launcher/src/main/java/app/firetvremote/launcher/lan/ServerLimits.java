package app.firetvremote.launcher.lan;

import java.net.InetAddress;

public final class ServerLimits {
    public static final int CLIENTS = 2;
    public static final int HEADER_BYTES = 8192;
    public static final int READ_TIMEOUT_MS = 1500;
    public static final long HEADER_TIMEOUT_NS = 4_000_000_000L;
    private ServerLimits() {}

    public static boolean privateV4(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length != 4) return false;
        int a = b[0] & 255, second = b[1] & 255;
        return a == 10 || a == 172 && second >= 16 && second <= 31
                || a == 192 && second == 168;
    }
}
