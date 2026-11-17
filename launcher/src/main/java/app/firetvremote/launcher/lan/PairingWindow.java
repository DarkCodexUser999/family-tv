package app.firetvremote.launcher.lan;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** No timers: expiry uses monotonic time and is checked at each operation. */
public final class PairingWindow {
    public interface Clock { long millis(); }
    private final Clock clock;
    private final SecureRandom random;
    private final PairingStore store;
    private String session, pin;
    private long expires, attemptsReset;
    private int guesses, total;
    private final Map<String,Integer> addresses = new LinkedHashMap<>();
    public PairingWindow(PairingStore store, Clock clock, SecureRandom random) {
        this.store=store; this.clock=clock; this.random=random;
    }
    private String randomHex(int bytes) { byte[] b=new byte[bytes]; random.nextBytes(b); return PairingStore.hex(b); }
    public synchronized String open() {
        session=randomHex(16); pin=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        expires=clock.millis()+120000; guesses=0;
        return pin;
    }
    public synchronized boolean isOpen() { return session!=null && clock.millis()<expires && guesses<5; }
    public synchronized String sessionId() { return isOpen()?session:null; }
    public synchronized void close() { session=null; pin=null; }
    public synchronized String pair(String address, Map<String,String> request) {
        long now=clock.millis();
        if (now>=attemptsReset) { addresses.clear(); total=0; attemptsReset=now+120000; }
        int used=addresses.containsKey(address)?addresses.get(address):0;
        if(total>=20 || used>=5 || (!addresses.containsKey(address)&&addresses.size()>=32)) return "rate_limited";
        total++; addresses.put(address,used+1);
        if(!isOpen()) return "unauthorized";
        guesses++;
        if(!session.equals(request.get("sessionId")) || !pin.equals(request.get("pin"))) return "unauthorized";
        if(store.count()>=8) { close(); return "busy"; }
        String id=randomHex(16), token=randomHex(32);
        close(); // Consume even on persistence failure; replay can never mint a second token.
        if(!store.add(id,token)) return "busy";
        return "{\"apiVersion\":1,\"deviceId\":\""+id+"\",\"token\":\""+token+"\"}";
    }
}
