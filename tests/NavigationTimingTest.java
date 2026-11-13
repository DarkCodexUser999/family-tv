package app.firetvremote.launcher;

public final class NavigationTimingTest {
    public static void main(String[] args) {
        check(!NavigationTiming.isRapid(-1, 0, 0), "first press should animate");
        check(NavigationTiming.isRapid(100, 150, 0), "fast individual presses should snap");
        check(NavigationTiming.isRapid(100, 1100, 1), "held key should snap even after long initial delay");
        check(!NavigationTiming.isRapid(100, 200, 0), "normal presses should animate");
        check(!NavigationTiming.isRapid(200, 100, 0), "old timestamps must not appear rapid");
        check(!NavigationTiming.isRapid(100, 1000, 0), "animations should recover after idle");
        System.out.println("PASS: navigation timing, rapid taps, held key, idle recovery");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
