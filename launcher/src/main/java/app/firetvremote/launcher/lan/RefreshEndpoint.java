package app.firetvremote.launcher.lan;

/** One foreground-phone job, no timers, threads, secrets or provider HTTP on TV. */
public final class RefreshEndpoint {
    public interface Clock { long now(); }
    private final Clock clock;
    private String id="",pkg="",owner="",state="idle";
    private long deadline;
    public RefreshEndpoint(){this(()->System.nanoTime()/1000000L);}
    public RefreshEndpoint(Clock clock){this.clock=clock;}
    private void expire(){if("running".equals(state)&&clock.now()>=deadline){owner="";state="phone_required";}}
    public synchronized String request(String target){
        expire();
        if(("running".equals(state)||"phone_required".equals(state))&&!pkg.equals(target))return null;
        if(!pkg.equals(target)||!("running".equals(state)||"phone_required".equals(state))){
            pkg=target;id=java.util.UUID.randomUUID().toString();state="phone_required";owner="";
        }
        return json();
    }
    public synchronized boolean claim(String phone,String job){
        expire();if(!id.equals(job)||!"phone_required".equals(state))return false;
        owner=phone;state="running";deadline=clock.now()+120000;return true;
    }
    public synchronized boolean finish(String phone,String job,String result){
        expire();if(!id.equals(job)||!owner.equals(phone)||!"running".equals(state))return false;
        if(!("complete".equals(result)||"canceled".equals(result)||"error".equals(result)))return false;
        state=result;owner="";return true;
    }
    public synchronized void disable(){state="idle";pkg="";id="";owner="";}
    public synchronized String state(){expire();return state;}
    public synchronized String json(){expire();return "{\"apiVersion\":1,\"jobId\":\""+id+"\",\"package\":\""+pkg+"\",\"state\":\""+state+"\"}";}
}
