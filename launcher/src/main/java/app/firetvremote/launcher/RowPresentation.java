package app.firetvremote.launcher;

/** Row placement in the existing 1920-wide design coordinate system. */
final class RowPresentation {
    private RowPresentation() {}

    static int top(int row) { return 683 + row * 260; }
    static int translation(int activeRow) { return -activeRow * 260; }
    static float alpha(int row, int activeRow) {
        return row == activeRow ? 1f : row == activeRow + 1 ? .5f : 0f;
    }
}
