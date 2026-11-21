package app.firetvremote.launcher;

import java.util.Set;

final class LaunchRoute {
    final String pkg;
    final String activity;
    final String category;
    final boolean ownTask;

    private LaunchRoute(String pkg, String activity, String category, boolean ownTask) {
        this.pkg = pkg;
        this.activity = activity;
        this.category = category;
        this.ownTask = ownTask;
    }

    static LaunchRoute build(String pkg, String activity, String category, Set<String> ownTask) {
        if (activity == null) return null;
        return new LaunchRoute(pkg, activity, category, ownTask.contains(pkg));
    }

    static void preferPrimeApp(Set<String> packages) {
        if (packages.contains("com.amazon.firebat")) packages.remove("com.amazon.avod");
    }
}
