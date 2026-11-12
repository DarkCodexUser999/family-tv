package app.firetvremote.launcher;

import android.content.ComponentName;
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

    static final class Entry {
        final ResolveInfo info;
        final String category;

        Entry(ResolveInfo info, String category) {
            this.info = info;
            this.category = category;
        }

        Intent intent() {
            ActivityInfo activity = info.activityInfo;
            return new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(activity.packageName, activity.name));
        }
    }

    static Set<String> hiddenPackages(Context context) {
        return readPackages(context, "hidden-apps.txt");
    }

    static Set<String> readPackages(Context context, String filename) {
        Set<String> hidden = new HashSet<>();
        try (BufferedReader config = new BufferedReader(new InputStreamReader(
                context.getAssets().open(filename), StandardCharsets.UTF_8))) {
            for (String line = config.readLine(); line != null; line = config.readLine()) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) hidden.add(name);
            }
        } catch (IOException e) {
            Log.w("FamilyTV", "Could not read " + filename, e);
        }
        return hidden;
    }

    static Entry find(Context context, String packageName) {
        PackageManager packages = context.getPackageManager();
        for (String category : CATEGORIES) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(packageName);
            for (ResolveInfo result : packages.queryIntentActivities(query, 0)) {
                if (launchable(context, result)) return new Entry(result, category);
            }
        }
        return null;
    }

    static Map<String, Entry> list(Context context) {
        PackageManager packages = context.getPackageManager();
        String ownPackage = context.getPackageName();
        Map<String, Entry> apps = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo result : packages.queryIntentActivities(query, 0)) {
                if (!launchable(context, result)) continue;
                String name = result.activityInfo.packageName;
                if (!name.equals(ownPackage) && !apps.containsKey(name)) {
                    apps.put(name, new Entry(result, category));
                }
            }
        }
        return apps;
    }

    private static boolean launchable(Context context, ResolveInfo result) {
        ActivityInfo activity = result.activityInfo;
        return activity != null && activity.exported && activity.enabled
                && activity.applicationInfo.enabled
                && (activity.permission == null || context.getPackageManager().checkPermission(
                        activity.permission, context.getPackageName()) == PackageManager.PERMISSION_GRANTED);
    }
}
