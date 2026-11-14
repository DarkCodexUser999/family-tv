package app.firetvremote;

public final class LinkTest {
    public static void main(String[] args) {
        String proof = Link.proof("Jefe", "what do ya want for nothing?");
        check(proof.equals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"), "HMAC vector");
        check(Link.matches(proof, proof), "equal proof rejected");
        check(!Link.matches(proof, proof.substring(1)), "truncated proof accepted");
        check(!Link.matches(Link.proof("key", "nonce-a:phone:OK"), Link.proof("key", "nonce-b:phone:OK")), "nonce not bound");
        check(!Link.matches(Link.proof("key", "nonce:phone:OK"), Link.proof("key", "nonce:phone:BACK")), "command not bound");
        check(!Link.phoneKey("PAIRINGCODE", "phone-a").equals(Link.phoneKey("PAIRINGCODE", "phone-b")), "phone keys not distinct");
        check(Link.randomText(24).matches("[A-Z2-9]{24}"), "challenge format");
        System.out.println("PASS: HMAC vector, proof comparison, nonce/action binding and phone key separation");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
