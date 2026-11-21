package app.firetvremote.launcher;

/** Snapshot of a launch target. Package-manager objects are used only by the artwork worker. */
final class CatalogEntry {
    final String label;
    final LaunchRoute route;

    CatalogEntry(String label, LaunchRoute route) {
        this.label = label;
        this.route = route;
    }
}
