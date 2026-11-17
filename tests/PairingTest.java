package app.firetvremote.launcher.lan;
import java.util.*;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
public final class PairingTest {
    static int checks;
    static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); checks++; }
    static final class Disk implements PairingStore.Disk {
        Map<String,String> data=new LinkedHashMap<>(); boolean fail;
        public Map<String,String> load(){return new LinkedHashMap<>(data);}
        public boolean save(Map<String,String> next){if(fail)return false; data=new LinkedHashMap<>(next);return true;}
    }
    static Map<String,String> request(PairingWindow w,String pin) {
        Map<String,String> r=new LinkedHashMap<>();r.put("sessionId",w.sessionId());r.put("pin",pin);r.put("deviceName","Phone");return r;
    }
    static String token(String response) { return response.split("\"token\":\"")[1].split("\"")[0]; }
    public static void main(String[] args) throws Exception {
        Disk disk=new Disk();PairingStore store=new PairingStore(disk);AtomicLong now=new AtomicLong();
        PairingWindow w=new PairingWindow(store,now::get,new SecureRandom());
        check(!w.isOpen(),"closed by default");
        String pin=w.open();Map<String,String> r=request(w,pin);
        Map<String,String> wrong=new LinkedHashMap<>(r);wrong.put("pin",pin.equals("000000")?"111111":"000000");
        check(w.pair("a",wrong).equals("unauthorized"),"wrong PIN");
        String paired=w.pair("a",r),token=token(paired);
        check(token.matches("[0-9a-f]{64}"),"256-bit token");
        check(!w.isOpen()&&w.pair("b",r).equals("unauthorized"),"one use/replay");
        check(store.authenticate(token)!=null,"valid auth");
        check(!disk.data.containsValue(token),"only hashes persisted");
        PairingStore restarted=new PairingStore(disk);
        check(restarted.authenticate(token)!=null,"survives restart");
        check(AuthFilter.phone(store,"Bearer "+token)!=null,"bearer gate");
        check(AuthFilter.phone(store,"bearer "+token)==null,"strict malformed auth");
        check(store.revokeAll()&&store.authenticate(token)==null,"revoke takes effect");
        check(new PairingStore(disk).authenticate(token)==null,"revoke persists");
        pin=w.open();r=request(w,pin);now.addAndGet(120000);
        check(w.pair("b",r).equals("unauthorized"),"exact expiry");
        pin=w.open();r=request(w,pin);w.close();check(!w.isOpen(),"explicit cancel");
        pin=w.open();r=request(w,pin);wrong=new LinkedHashMap<>(r);wrong.put("pin","bad");
        for(int i=0;i<5;i++)w.pair("guess",wrong);
        check(!w.isOpen(),"five guesses close window");
        pin=w.open();check(w.pair("guess",request(w,pin)).equals("rate_limited"),"reopening cannot reset address throttle");
        now.addAndGet(120000);pin=w.open();r=request(w,pin);disk.fail=true;
        check(w.pair("ok",r).equals("busy")&&!w.isOpen()&&store.count()==0,"failed disk cannot issue token");disk.fail=false;
        for(int i=0;i<8;i++){now.addAndGet(120000);pin=w.open();check(w.pair("ok",request(w,pin)).startsWith("{"),"phone pair "+i);}
        now.addAndGet(120000);pin=w.open();check(w.pair("ok",request(w,pin)).equals("busy"),"eight phone cap");
        for(String bad:new String[]{"{}","{\"pin\":\"000000\",\"pin\":\"000000\",\"deviceName\":\"x\"}",
                "{\"pin\":0,\"sessionId\":\"a\",\"deviceName\":\"x\"}",
                "{\"pin\":\"000000\",\"sessionId\":\"a\",\"deviceName\":\"x\"} trailing"}) {
            try{PairJson.parse(bad);throw new AssertionError("bad JSON accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        check(PairJson.parse("{\"pin\":\"000000\",\"sessionId\":\"a\",\"deviceName\":\"Mom\\u0020phone\"}").get("deviceName").equals("Mom phone"),"escaped name");
        System.out.println("PASS: "+checks+" pairing/auth/persistence/strict JSON checks");
    }
}
