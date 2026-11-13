package app.firetvremote.launcher;

public final class RowPresentationTest {
    public static void main(String[] args) {
        for (int count = 1; count <= 4; count++) {
            // Exercise DOWN then UP, including a single row and both boundaries.
            for (int step = 0; step < count * 2 - 1; step++) {
                int active = step < count ? step : count * 2 - 2 - step;
                for (int row = 0; row < count; row++) {
                    float expected = row == active ? 1f : row == active + 1 ? .5f : 0f;
                    check(RowPresentation.alpha(row, active) == expected, "wrong visibility");
                    int screenTop = RowPresentation.top(row) + RowPresentation.translation(active);
                    if (row == active) check(screenTop == 683, "focused row left bottom slot");
                    if (row == active + 1) {
                        int tileTop = screenTop + 32;
                        int visibleHeight = 1026 - tileTop;
                        check(visibleHeight >= 50 && visibleHeight <= 52, "peek is not a quarter tile");
                    }
                    if (row < active) check(expected == 0f, "old row obscures backdrop");
                }
            }
        }
        System.out.println("PASS: 1–4 rows, DOWN/UP, focused slot, prior-row hiding, 25% peek");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
