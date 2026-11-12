package app.firetvremote;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Link {
    public static final int PORT = 48321;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private Link() {}

    public static String randomText(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }

    public static String proof(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    public static String phoneKey(String code, String id) {
        return proof(code, "family-tv-key:" + id);
    }

    public static void exchange(String host, String id, String key, String action) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, PORT), 2000);
            socket.setSoTimeout(2500);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            String nonce = input.readUTF();
            if (!nonce.matches("[A-Z2-9]{24}")) throw new IOException("Invalid TV challenge");
            String request = nonce + ":" + id + ":" + action;
            output.writeUTF(id);
            output.writeUTF(action);
            output.writeUTF(proof(key, "request:" + request));
            output.flush();
            if (!matches(proof(key, "accepted:" + request), input.readUTF())) {
                throw new IOException("TV did not accept the request");
            }
        }
    }
}
