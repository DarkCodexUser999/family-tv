package app.firetvremote.remote;

public final class RemoteInputTest {
    public static void main(String[] args) {
        for (String value : new String[]{"127.0.0.1", "10.0.0.12", "255.255.255.255", "0.0.0.0"}) {
            check(RemoteInput.validAddress(value), "valid IPv4 rejected");
        }
        for (String value : new String[]{null, "", "1.2.3.4.", ".1.2.3.4", "1..3.4", "1.2.3", "256.1.2.3", "123456.1.2.3", "1.2.3.-4", "1.2.3.x", "1.2.3.4:5555", "1.2.3.4 "}) {
            check(!RemoteInput.validAddress(value), "invalid IPv4 accepted: " + value);
        }
        check(RemoteInput.freshCommand(1, 1, 100, 850), "750 ms boundary rejected");
        check(!RemoteInput.freshCommand(1, 1, 100, 851), "stale key dispatched");
        check(!RemoteInput.freshCommand(1, 2, 100, 101), "old endpoint dispatched");
        check(!RemoteInput.freshCommand(1, 1, 100, 99), "invalid timestamp dispatched");
        System.out.println("PASS: strict IPv4, stale queued commands, endpoint changes");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
