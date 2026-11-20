package app.firetvremote.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import app.firetvremote.launcher.lan.LauncherBridge;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Network and key access occur on the refresh worker only. The UI reads immutable snapshots. */
final class TmdbShelf {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final int MAX_IMAGES = 30;
    private final Context app;
    private final File directory;
    private final SharedPreferences prefs;
    private volatile Map<String,List<File>> snapshot = Collections.emptyMap();
    private volatile boolean closed;

    private static final Map<String,String> PROVIDERS = new LinkedHashMap<>();
    static {
        PROVIDERS.put("com.netflix.ninja", "Netflix");
        PROVIDERS.put("com.amazon.firebat", "Amazon Prime Video");
        PROVIDERS.put("com.amazon.avod", "Amazon Prime Video");
        PROVIDERS.put("in.startv.hotstar", "JioHotstar");
        PROVIDERS.put("com.jio.media.stb.ondemand", "JioHotstar");
        PROVIDERS.put("com.graymatrix.did", "Zee5");
        PROVIDERS.put("com.zee5.aTV", "Zee5");
        PROVIDERS.put("com.discovery.discoveryplus.firetv", "Discovery+");
        PROVIDERS.put("com.discovery.discoveryplus.androidtv", "Discovery+");
    }
    TmdbShelf(Context context) {
        app = context.getApplicationContext();
        directory = new File(app.getCacheDir(), "tmdb-w1280");
        prefs = app.getSharedPreferences("tmdb-shelf-v1", Context.MODE_PRIVATE);
    }
    List<File> images(String pkg) {
        List<File> list = snapshot.get(PROVIDERS.get(pkg));
        return list == null ? Collections.emptyList() : list;
    }
    void close() { closed = true; }
    private boolean online() {
        ConnectivityManager manager = (ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = manager == null ? null : manager.getActiveNetworkInfo();
        return network != null && network.isConnected();
    }
    private String credential() {
        try (InputStream in = new FileInputStream(new File(Environment.getExternalStorageDirectory(),
                "FamilyTV/tmdb_key.txt"))) {
            String key = new String(read(in, 4096), StandardCharsets.UTF_8).trim();
            return key.matches("[a-fA-F0-9]{32}|[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+") ? key : "";
        } catch (IOException | SecurityException absent) { return ""; }
    }
    // Activity recreation can briefly overlap two instances writing the same private cache.
    private static final Object REFRESH_LOCK = new Object();
    void refresh(Runnable changed) {
        synchronized (REFRESH_LOCK) {
            if (!closed) refreshLocked(changed);
        }
    }
    private void refreshLocked(Runnable changed) {
        try {
            String key = credential();
            if (closed) return;
            LauncherBridge.SHARED.tmdbConfigured = !key.isEmpty();
            if (key.isEmpty() || !online()) {
                snapshot = Collections.emptyMap();
                changed.run();
                return;
            }
            publish(changed);
            long now = System.currentTimeMillis(), last = prefs.getLong("attempt", 0);
            if (last > 0 && now >= last && now-last < DAY) return;
            if (!prefs.edit().putLong("attempt",now).commit()) return;
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            String today = date(now), since = date(now-90*DAY);
            Map<String,List<Candidate>> picks = new LinkedHashMap<>();
            for (String type : new String[]{"movie","tv"}) {
                JSONObject response = api("watch/providers/"+type+"?watch_region=IN",key);
                JSONArray providers = response.getJSONArray("results");
                for (String name : new LinkedHashSet<>(PROVIDERS.values())) {
                    int id = providerId(providers,name);
                    if (id <= 0) continue;
                    String field = type.equals("movie") ? "primary_release_date" : "first_air_date";
                    JSONObject titles = api("discover/"+type+"?watch_region=IN&with_watch_providers="+id
                            +"&sort_by=popularity.desc&"+field+".gte="+since+"&"+field+".lte="+today,key);
                    List<Candidate> list = picks.get(name);
                    if (list == null) { list = new ArrayList<>(); picks.put(name,list); }
                    JSONArray results = titles.getJSONArray("results");
                    for (int i=0;i<results.length();i++) {
                        JSONObject title = results.getJSONObject(i);
                        String path = title.optString("backdrop_path");
                        if (path.matches("/[A-Za-z0-9_-]+\\.jpg"))
                            list.add(new Candidate(path.substring(1),title.optDouble("popularity",0)));
                    }
                }
            }
            SharedPreferences.Editor editor = prefs.edit();
            for (String name : new LinkedHashSet<>(PROVIDERS.values())) {
                List<Candidate> list = picks.get(name);
                LinkedHashSet<String> names = new LinkedHashSet<>();
                if (list != null) {
                    Collections.sort(list, (a,b) -> Double.compare(b.score,a.score));
                    for (Candidate c : list) {
                        if (closed) return;
                        if (!names.contains(c.file) && download(c.file)) names.add(c.file);
                        if (names.size()==5) break;
                    }
                }
                editor.putString(name, android.text.TextUtils.join(",",names));
            }
            if (!closed && editor.commit()) publish(changed);
        } catch (Exception unavailable) {
            // Never log provider exceptions: a legacy API-key URL can occur in their message.
        }
    }

    private void publish(Runnable changed) {
        Map<String,List<File>> next = new HashMap<>();
        for (String name : new LinkedHashSet<>(PROVIDERS.values())) {
            List<File> files = new ArrayList<>();
            for (String token : prefs.getString(name,"").split(",")) {
                if (!token.matches("[A-Za-z0-9_-]+\\.jpg")) continue;
                File f = new File(directory,token);
                if (f.isFile()) files.add(f);
            }
            next.put(name, Collections.unmodifiableList(files));
        }
        if (!closed) { snapshot = next; changed.run(); }
    }
    private static int providerId(JSONArray providers, String name) throws org.json.JSONException {
        for (int i=0;i<providers.length();i++) {
            JSONObject p = providers.getJSONObject(i);
            if (name.equalsIgnoreCase(p.optString("provider_name"))) return p.getInt("provider_id");
        }
        return -1;
    }
    private static final class Candidate {
        final String file; final double score;
        Candidate(String file,double score) { this.file=file; this.score=score; }
    }
    private static String date(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC")); return fmt.format(new Date(millis));
    }
    private HttpURLConnection connection(String url) throws IOException {
        if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(4500); conn.setReadTimeout(5500);
        conn.setInstanceFollowRedirects(false); conn.setUseCaches(false);
        return conn;
    }
    private JSONObject api(String route,String key) throws Exception {
        boolean bearer = key.indexOf('.')>=0;
        HttpURLConnection conn = connection("https://api.themoviedb.org/3/"+route+(bearer?"":"&api_key="+key));
        try {
            if (bearer) conn.setRequestProperty("Authorization","Bearer "+key);
            if (conn.getResponseCode()!=200) throw new IOException();
            try (InputStream in=conn.getInputStream()) {
                return new JSONObject(new String(read(in,512*1024),StandardCharsets.UTF_8));
            }
        } finally { conn.disconnect(); }
    }
    private static byte[] read(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096]; int n;
        while ((n=in.read(buffer))!=-1) {
            if (out.size()+n>limit) throw new IOException();
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
    private boolean download(String name) throws IOException {
        File target=new File(directory,name);
        if (target.isFile() && valid(target)) { target.setLastModified(System.currentTimeMillis()); return true; }
        HttpURLConnection conn=connection("https://image.tmdb.org/t/p/w1280/"+name);
        File temp=File.createTempFile("download-", ".tmp",directory);
        try {
            if (conn.getResponseCode()!=200) return false;
            try (InputStream in=conn.getInputStream(); OutputStream out=new FileOutputStream(temp)) {
                byte[] buf=new byte[8192]; int total=0,n;
                while ((n=in.read(buf))!=-1) {
                    if (closed || (total+=n)>4*1024*1024) return false;
                    out.write(buf,0,n);
                }
            }
            if (!valid(temp)) return false;
            File[] jpg=directory.listFiles((d,f)->f.endsWith(".jpg"));
            if (jpg==null) return false;
            Arrays.sort(jpg,(a,b)->Long.compare(a.lastModified(),b.lastModified()));
            for (int i=0;i<=jpg.length-MAX_IMAGES;i++) if (!jpg[i].delete()) return false;
            return !closed && temp.renameTo(target);
        } finally { conn.disconnect(); temp.delete(); }
    }
    private static boolean valid(File f) {
        try (RandomAccessFile bytes = new RandomAccessFile(f, "r")) {
            long size = bytes.length();
            if (size < 4 || size > 4*1024*1024 || bytes.readUnsignedShort() != 0xffd8) return false;
            bytes.seek(size-2);
            if (bytes.readUnsignedShort() != 0xffd9) return false;
        } catch (IOException invalid) { return false; }
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(f.getPath(),o);
        return "image/jpeg".equals(o.outMimeType) && o.outWidth>0 && o.outWidth<=1280
                && o.outHeight>0 && o.outHeight<=1280 && o.outWidth>o.outHeight;
    }
}
