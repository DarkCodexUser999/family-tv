package app.firetvremote.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class AppCatalog {
    private static final String[] CATEGORIES = {
        Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER
    };

    interface ArtworkSource {
        void register(String pkg, ResolveInfo info);
    }

    static Intent intent(LaunchRoute route) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(route.category)
                .setComponent(new android.content.ComponentName(route.pkg, route.activity));
        if (route.ownTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    static Set<String> hiddenPackages(Context context) {
        return readPackages(context, "hidden-apps.txt", java.util.Arrays.asList(
                "com.amazon.alexashopping",
                "com.amazon.gamehub",
                "com.amazon.tahoe",
                "com.amazon.shoptv.firetv.client",
                "com.amazon.bueller.photos",
                "com.amazon.hedwig",
                "com.amazon.minitv.android.app",
                "com.amazon.bueller.music",
                "com.imdb.livingroom.firetv",
                "com.amazon.imdb.tv.android.app",
                "com.onemainstream.sonyliv.android"));
    }

    static Set<String> readPackages(Context context, String filename) {
        return readPackages(context, filename, java.util.Collections.emptyList());
    }

    private static Set<String> readPackages(Context context, String filename,
            java.util.List<String> fallback) {
        Set<String> packages = new HashSet<>();
        try (BufferedReader config = new BufferedReader(new InputStreamReader(
                context.getAssets().open(filename), StandardCharsets.UTF_8))) {
            for (String line = config.readLine(); line != null; line = config.readLine()) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) packages.add(name);
            }
        } catch (IOException | SecurityException e) {
            Log.w("FamilyTV", "Could not read " + filename, e);
            packages.addAll(fallback);
        }
        return packages;
    }

    static Map<String, CatalogEntry> list(Context context, Set<String> hidden, Set<String> ownTask,
            ArtworkSource artwork) {
        PackageManager packages = context.getPackageManager();
        Map<String, ResolveInfo> discovered = new LinkedHashMap<>();
        Map<String, String> categories = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo result : packages.queryIntentActivities(query, 0)) {
                if (!launchable(context, result)) continue;
                String pkg = result.activityInfo.packageName;
                if (pkg.equals(context.getPackageName()) || hidden.contains(pkg)
                        || discovered.containsKey(pkg)) continue;
                discovered.put(pkg, result);
                categories.put(pkg, category);
            }
        }
        LaunchRoute.preferPrimeApp(discovered.keySet());
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, ResolveInfo> app : discovered.entrySet()) {
            String pkg = app.getKey();
            ResolveInfo info = app.getValue();
            LaunchRoute route = LaunchRoute.build(pkg, info.activityInfo.name, categories.get(pkg), ownTask);
            if (route == null) continue;
            try {
                String label = info.loadLabel(packages).toString();
                artwork.register(pkg, info);
                entries.put(pkg, new CatalogEntry(label, route));
            } catch (RuntimeException unavailable) {
                Log.w("FamilyTV", "Skipping unavailable app " + pkg, unavailable);
            }
        }
        return entries;
    }

    private static boolean launchable(Context context, ResolveInfo result) {
        ActivityInfo activity = result.activityInfo;
        return activity != null && activity.exported && activity.enabled
                && activity.applicationInfo != null && activity.applicationInfo.enabled
                && (activity.permission == null || context.getPackageManager().checkPermission(
                        activity.permission, context.getPackageName()) == PackageManager.PERMISSION_GRANTED);
    }
}
