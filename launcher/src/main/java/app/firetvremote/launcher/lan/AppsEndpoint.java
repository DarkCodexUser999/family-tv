package app.firetvremote.launcher.lan;

import java.util.*;

/** Immutable bounded catalog, prepared off the UI thread. No package or image work on requests. */
public final class AppsEndpoint {
    public static final int MAX_APPS = 64, MAX_ICON_BYTES = 8192;
    public static final class App {
        public final String pkg, label;
        private final byte[] icon;
        public App(String pkg, String label, byte[] icon) {
            if (!packageName(pkg) || label == null || label.isEmpty()) throw new IllegalArgumentException();
            this.pkg=pkg; this.label=label.substring(0,Math.min(80,label.length()));
            this.icon=icon!=null && icon.length<=MAX_ICON_BYTES ? icon.clone() : null;
        }
    }
    private final List<App> apps;
    public final long revision;
    public AppsEndpoint(long revision, List<App> source) {
        if(revision<0 || source.size()>MAX_APPS) throw new IllegalArgumentException();
        Set<String> seen=new HashSet<>();
        for(App app:source) if(!seen.add(app.pkg)) throw new IllegalArgumentException();
        this.revision=revision;apps=Collections.unmodifiableList(new ArrayList<>(source));
    }
    public static boolean packageName(String pkg) {
        return pkg!=null && pkg.length()<=160 && pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }
    public boolean contains(String pkg) { for(App app:apps) if(app.pkg.equals(pkg))return true;return false; }
    public byte[] icon(String pkg) { for(App app:apps)if(app.pkg.equals(pkg))return app.icon==null?null:app.icon.clone();return null; }
    public AppsEndpoint visible(Set<String> hidden) {
        List<App> visible=new ArrayList<>();for(App app:apps)if(!hidden.contains(app.pkg))visible.add(app);
        return new AppsEndpoint(revision,visible);
    }
    public String page(String cursor,int limit){return page(cursor,limit,Collections.emptySet());}
    public String page(String cursor,int limit,Set<String> hidden) {
        if(limit<1 || limit>25)throw new IllegalArgumentException();
        int start=0;
        if(cursor!=null) {
            if(!cursor.matches("[0-9]{1,15}_[0-9]{1,2}"))throw new IllegalArgumentException();
            String[] fields=cursor.split("_");
            if(Long.parseLong(fields[0])!=revision)throw new IllegalStateException("stale_catalog");
            start=Integer.parseInt(fields[1]);
            if(start>=apps.size())throw new IllegalArgumentException();
        }
        int end=Math.min(start+limit,apps.size());
        StringBuilder json=new StringBuilder("{\"apiVersion\":1,\"revision\":"+revision+",\"apps\":[");
        for(int i=start;i<end;i++) {
            App app=apps.get(i);if(i>start)json.append(',');
            json.append("{\"package\":").append(quote(app.pkg)).append(",\"label\":").append(quote(app.label))
                .append(",\"hidden\":").append(hidden.contains(app.pkg)).append(",\"iconRevision\":").append(revision).append('}');
        }
        return json.append("],\"nextCursor\":").append(end<apps.size()?quote(revision+"_"+end):"null").append('}').toString();
    }
    private static String quote(String text) {
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(c=='\"'||c=='\\')out.append('\\').append(c);
            else if(c<32 || Character.isSurrogate(c))out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
            else out.append(c);
        }return out.append('\"').toString();
    }
}
