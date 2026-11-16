package app.firetvremote.launcher.lan;

import android.content.Context;
import android.security.KeyPairGeneratorSpec;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.security.auth.x500.X500Principal;

/** Platform keystore only; no hidden Android APIs, certificate libraries or exported keys. */
final class TlsIdentity {
    private static final String ALIAS = "family-tv-lan-v1";
    final SSLServerSocketFactory sockets;
    final String fingerprint;

    TlsIdentity(Context context) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            Calendar end = Calendar.getInstance(); end.add(Calendar.YEAR, 5);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS)
                    .setKeySize(2048).setSubject(new X500Principal("CN=Family TV"))
                    .setSerialNumber(BigInteger.ONE).setStartDate(new Date(System.currentTimeMillis()-86400000L))
                    .setEndDate(end.getTime()).build());
            generator.generateKeyPair();
            store.load(null);
        }
        ((java.security.cert.X509Certificate)store.getCertificate(ALIAS)).checkValidity();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(store.getCertificate(ALIAS).getEncoded());
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        fingerprint = hex.toString();
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, null);
        SSLContext tls = SSLContext.getInstance("TLSv1.2");
        tls.init(keys.getKeyManagers(), null, null);
        sockets = tls.getServerSocketFactory();
    }
}
