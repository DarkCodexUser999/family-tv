package app.firetvremote.launcher;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CatalogRowsTest {
    private static ResolveInfo result(String pkg, String label) {
        ResolveInfo result = new ResolveInfo();
        result.activityInfo = new ActivityInfo();
        result.activityInfo.packageName = pkg;
        result.activityInfo.name = "Main";
        result.label = label;
        return result;
    }

    public static void main(String[] args) {
        Context context = new Context();
        ResolveInfo browser = result("browser", "Browser");
        context.pm.entries.addAll(Arrays.asList(browser, result("zee", "ZEE5"),
                result("other", "Other"), result("hidden", "Hidden")));
        Map<String, CatalogEntry> entries = AppCatalog.list(context, Collections.singleton("hidden"),
                Collections.singleton("browser"), (pkg, info) -> {});
        browser.activityInfo.name = "Replacement";
        browser.label = "Renamed";
        CatalogEntry original = entries.get("browser");
        if (!original.label.equals("Browser") || !original.route.activity.equals("Main")
                || !original.route.ownTask || entries.containsKey("hidden")) throw new AssertionError();

        List<List<CatalogEntry>> rows = CatalogRows.arrange(entries, Arrays.asList(
                Arrays.asList("missing", "browser"), Collections.singletonList("browser"),
                Collections.emptyList(), Collections.emptyList(), Collections.singletonList("other")));
        if (!rows.equals(Arrays.asList(Collections.singletonList(original),
                Collections.singletonList(entries.get("zee")), Collections.singletonList(entries.get("other")))))
            throw new AssertionError("Configured rows, streaming fallback or duplicates");
        if (!CatalogRows.arrange(entries, Collections.emptyList()).get(0).equals(
                Arrays.asList(original, entries.get("zee"), entries.get("other"))))
            throw new AssertionError("Fallback lost discovery order");
        if (!CatalogRows.arrange(Collections.emptyMap(), Collections.emptyList()).isEmpty())
            throw new AssertionError("Empty catalog");
        System.out.println("PASS: catalog snapshot, configured rows, streaming fallback and empty rows");
    }
}
