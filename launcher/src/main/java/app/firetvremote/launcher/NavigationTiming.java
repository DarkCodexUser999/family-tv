package app.firetvremote.launcher;

/** Rapid key sequences should not trail behind animated focus/scroll positions. */
final class NavigationTiming {
    private NavigationTiming() {}
    static boolean isRapid(long previousDown, long now, int repeats) {
        return repeats > 0 || (previousDown >= 0 && now >= previousDown && now - previousDown < 100);
    }
}
