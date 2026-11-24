package app.firetvremote.launcher.lan;

import java.io.IOException;
import java.util.*;

/** Small revisioned document; only writes touch disk, always on an HTTP/artwork worker. */
public final class RowsEndpoint {
    public interface Disk { String read() throws IOException; void write(String json) throws IOException; }
    public interface Guard { boolean authorized(); }
    public static final class State {
        public final long revision;
        public final List<List<String>> rows;
        public State(long revision,List<List<String>> rows) {
            if(revision<0 || revision>999999999999999L || rows.isEmpty() || rows.size()>4)throw new IllegalArgumentException();
            Set<String> seen=new HashSet<>();List<List<String>> copy=new ArrayList<>();
            for(List<String> row:rows) {
                if(row.size()>6)throw new IllegalArgumentException();
                for(String pkg:row)if(!AppsEndpoint.packageName(pkg)||!seen.add(pkg))throw new IllegalArgumentException();
                copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
            }
            this.revision=revision;this.rows=Collections.unmodifiableList(copy);
        }
        public String json() {
            StringBuilder out=new StringBuilder("{\"apiVersion\":1,\"revision\":"+revision+",\"rows\":[");
            for(int i=0;i<rows.size();i++) {
                if(i>0)out.append(',');out.append("{\"packages\":[");List<String> row=rows.get(i);
                for(int j=0;j<row.size();j++){if(j>0)out.append(',');out.append('"').append(row.get(j)).append('"');}
                out.append("]}");
            }return out.append("]}").toString();
        }
    }
    private final Disk disk;
    private volatile State current;
    public RowsEndpoint(Disk disk,State defaults) {
        this.disk=disk;current=defaults;
        try{String saved=disk.read();if(saved!=null)current=parse(saved);}
        catch(IOException|IllegalArgumentException invalid){/* Invalid/interrupted overrides fall back safely. */}
    }
    public State snapshot(){return current;}
    @SuppressWarnings("unchecked")
    public static State parse(String json) {
        ActionMessage.Reader reader=new ActionMessage.Reader(json,true);
        Map<String,Object> root=reader.object(0);reader.space();
        if(reader.pos!=json.length() || root.size()!=3 || !Long.valueOf(1).equals(root.get("apiVersion"))
                || !(root.get("revision") instanceof Long) || !(root.get("rows") instanceof List))throw new IllegalArgumentException();
        List<List<String>> rows=new ArrayList<>();
        for(Object value:(List<?>)root.get("rows")) {
            if(!(value instanceof Map))throw new IllegalArgumentException();Map<?,?> row=(Map<?,?>)value;
            if(row.size()!=1 || !(row.get("packages") instanceof List))throw new IllegalArgumentException();
            List<String> packages=new ArrayList<>();
            for(Object pkg:(List<?>)row.get("packages")){if(!(pkg instanceof String))throw new IllegalArgumentException();packages.add((String)pkg);}
            rows.add(packages);
        }return new State((Long)root.get("revision"),rows);
    }
    public synchronized String replace(String json,String ifMatch,AppsEndpoint apps,Guard guard) {
        final State draft;
        try{draft=parse(json);}catch(IllegalArgumentException bad){return "invalid_request";}
        if(!('"'+Long.toString(draft.revision)+'"').equals(ifMatch))return "invalid_request";
        if(draft.revision!=current.revision || current.revision==999999999999999L)return "conflict";
        if(apps==null)return "catalog_unavailable";
        for(List<String> row:draft.rows)for(String pkg:row)if(!apps.contains(pkg))return "not_found";
        if(!guard.authorized())return "unauthorized";
        State next=new State(current.revision+1,draft.rows);
        try{disk.write(next.json());}catch(IOException failed){return "storage_unavailable";}
        current=next;return null;
    }
}
