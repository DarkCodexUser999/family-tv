package app.firetvremote.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CatalogRows {
    static final int LIMIT = 4;

    static List<List<CatalogEntry>> arrange(Map<String, CatalogEntry> installed,
            List<List<String>> definitions) {
        Map<String, CatalogEntry> remaining = new LinkedHashMap<>(installed);
        List<List<CatalogEntry>> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(LIMIT, definitions.size()); i++) {
            List<CatalogEntry> row = new ArrayList<>();
            for (String pkg : definitions.get(i)) {
                CatalogEntry entry = remaining.remove(pkg);
                if (entry != null) row.add(entry);
            }
            rows.add(row);
        }
        if (rows.isEmpty()) rows.add(new ArrayList<>());
        for (CatalogEntry entry : remaining.values()) {
            String label = entry.label.toLowerCase(Locale.ROOT);
            int row = rows.size() - 1;
            if (rows.size() > 1 && (label.contains("zee5") || label.contains("discovery"))) row = 1;
            rows.get(row).add(entry);
        }
        for (int row = rows.size() - 1; row >= 0; row--) {
            if (rows.get(row).isEmpty()) rows.remove(row);
        }
        return rows;
    }
}
