package app.firetvremote.launcher;

public final class RowFocusMemoryTest {
    public static void main(String[] args) {
        RowFocusMemory memory = new RowFocusMemory(new String[][] {
            {"app:netflix", "app:prime", "app:youtube", "app:hotstar"},
            {"app:store", "app:downloads"}, {}, {"app:browser"}
        });
        memory.remember(0, 3);
        check(memory.selection(1, memory.selection(0, 0)) == 1, "new short row must clamp source column");
        memory.remember(1, 0);
        check(memory.selection(0, 0) == 3, "UP must restore prior tile");
        check(memory.selection(1, 3) == 0, "return DOWN must remember target tile");
        check(memory.rowOf("app:browser") == 3, "saved key must locate lazy lower row");
        RowFocusMemory reordered = new RowFocusMemory(new String[][] {{"app:browser"}, {"app:netflix"}});
        check(reordered.rowOf("app:browser") == 0, "restore must follow package after reorder");
        check(reordered.rowOf("app:uninstalled") == -1, "removed or hidden package must fall back");
        check(memory.rowOf(null) == -1, "first launch has no saved key");
        check(memory.selection(2, 3) == -1, "empty row must not select a tile");
        check(memory.selection(-1, 0) == -1, "invalid row must not select a tile");
        memory.remember(0, 99);
        check(memory.selection(0, 0) == 3, "invalid updates must not erase focus");
        check(new RowFocusMemory(new String[0][]).rowOf("any") == -1, "empty catalog lookup");
        System.out.println("PASS: row return, first visit, short rows, lazy restore, reordered/removed apps");
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
