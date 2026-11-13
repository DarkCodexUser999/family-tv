package app.firetvremote.launcher;

/** Session-only row selections; the Activity persists the last app key separately. */
final class RowFocusMemory {
    private final String[][] keys;
    private final int[] columns;

    RowFocusMemory(String[][] keys) {
        this.keys = keys;
        columns = new int[keys.length];
        java.util.Arrays.fill(columns, -1);
    }

    void remember(int row, int column) {
        if (row >= 0 && row < keys.length && column >= 0 && column < keys[row].length) {
            columns[row] = column;
        }
    }

    int selection(int row, int fallback) {
        if (row < 0 || row >= keys.length || keys[row].length == 0) return -1;
        return columns[row] >= 0 ? columns[row]
                : Math.max(0, Math.min(fallback, keys[row].length - 1));
    }

    int rowOf(String key) {
        if (key == null) return -1;
        for (int row = 0; row < keys.length; row++) {
            for (String candidate : keys[row]) if (key.equals(candidate)) return row;
        }
        return -1;
    }
}
