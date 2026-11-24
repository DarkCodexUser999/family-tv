package app.firetvremote.launcher.lan;
import java.io.IOException;
import java.util.*;

/** Complete private override; bundled defaults remain intact and are the corruption fallback. */
public final class HiddenEndpoint {
    public static final class State {
        public final long revision;public final Set<String> packages;
        public State(long revision,Collection<String> source){
            if(revision<0||revision>=999999999999999L||source.size()>256)throw new IllegalArgumentException();
            Set<String> copy=new LinkedHashSet<>();
            for(String pkg:source)if(!AppsEndpoint.packageName(pkg)||!copy.add(pkg))throw new IllegalArgumentException();
            this.revision=revision;packages=Collections.unmodifiableSet(copy);
        }
        public String json(){StringBuilder out=new StringBuilder("{\"apiVersion\":1,\"revision\":"+revision+",\"packages\":[");
            for(String pkg:packages){if(out.charAt(out.length()-1)!='[')out.append(',');out.append('"').append(pkg).append('"');}
            return out.append("]}").toString();}
    }
    private final RowsEndpoint.Disk disk;private final Set<String> defaults;private volatile State state;
    public HiddenEndpoint(RowsEndpoint.Disk disk,Set<String> defaults){this.disk=disk;this.defaults=new HashSet<>(defaults);state=new State(0,defaults);
        try{String saved=disk.read();if(saved!=null)state=parse(saved);}catch(IOException|IllegalArgumentException bad){/* Preserve bundled exclusions on corruption. */}}
    public State snapshot(){return state;}
    public static State parse(String json){
        ActionMessage.Reader reader=new ActionMessage.Reader(json,true);Map<String,Object> root=reader.object(0);reader.space();
        if(reader.pos!=json.length()||root.size()!=3||!Long.valueOf(1).equals(root.get("apiVersion"))||!(root.get("revision") instanceof Long)||!(root.get("packages") instanceof List))throw new IllegalArgumentException();
        List<String> packages=new ArrayList<>();for(Object pkg:(List<?>)root.get("packages")){if(!(pkg instanceof String))throw new IllegalArgumentException();packages.add((String)pkg);}
        return new State((Long)root.get("revision"),packages);
    }
    public synchronized String replace(String body,String match,AppsEndpoint management,RowsEndpoint.Guard guard){
        final State draft;try{draft=parse(body);}catch(IllegalArgumentException bad){return "invalid_request";}
        if(!("\""+draft.revision+"\"").equals(match))return "invalid_request";
        if(draft.revision!=state.revision)return "conflict";
        if(management==null)return "catalog_unavailable";
        for(String pkg:draft.packages)if(!management.contains(pkg)&&!state.packages.contains(pkg)&&!defaults.contains(pkg))return "not_found";
        if(!guard.authorized())return "unauthorized";
        State next=new State(state.revision+1,draft.packages);
        try{disk.write(next.json());}catch(IOException failed){return "storage_unavailable";}
        state=next;return null;
    }
}
