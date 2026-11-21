package app.firetvremote.launcher;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class RowConfig {
    static List<List<String>> read(Context context) {
        try (InputStream input = context.getAssets().open("rows.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray rows = new JSONObject(bytes.toString("UTF-8")).getJSONArray("rows");
            List<List<String>> definitions = new ArrayList<>();
            for (int i = 0; i < Math.min(CatalogRows.LIMIT, rows.length()); i++) {
                JSONArray packages = rows.getJSONObject(i).getJSONArray("packages");
                List<String> row = new ArrayList<>();
                for (int j = 0; j < packages.length(); j++) row.add(packages.getString(j));
                definitions.add(row);
            }
            return definitions;
        } catch (IOException | JSONException | SecurityException invalid) {
            Log.w("FamilyTV", "Could not read rows.json", invalid);
            return Collections.emptyList();
        }
    }
}
