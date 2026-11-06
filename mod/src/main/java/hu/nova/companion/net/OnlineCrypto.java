package hu.nova.companion.net;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Végponttól végpontig titkosítás az internetes kapcsolathoz.
 * Az online kódból PBKDF2-vel (SHA-256, 20 000 kör) származtatjuk:
 *  - a csatorna (MQTT topic) azonosítóját – így a kód maga sosem kerül a hálózatra,
 *  - és egy AES-256-GCM kulcsot – a nyilvános szerver üzemeltetője sem látja az adatokat.
 * A telefonos felület (online.js) ugyanezt csinálja Web Crypto API-val.
 */
public final class OnlineCrypto {

    public static final int ITERATIONS = 20000;
    private static final byte[] SALT = "nova-companion-online-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    public final String topic;
    private final SecretKeySpec key;

    private OnlineCrypto(String topic, SecretKeySpec key) {
        this.topic = topic;
        this.key = key;
    }

    /** Kód normalizálása: csak betűk és számok, nagybetűvel (így a kötőjel / szóköz nem számít). */
    public static String normalize(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static OnlineCrypto fromCode(String code) throws Exception {
        String norm = normalize(code);
        PBEKeySpec spec = new PBEKeySpec(norm.toCharArray(), SALT, ITERATIONS, 64 * 8);
        byte[] bits = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 16; i++) hex.append(String.format("%02x", bits[i]));
        byte[] k = new byte[32];
        System.arraycopy(bits, 32, k, 0, 32);
        return new OnlineCrypto("novamc/" + hex, new SecretKeySpec(k, "AES"));
    }

    /** IV (12 bájt) + titkosított adat + 16 bájtos hitelesítő címke */
    public byte[] encrypt(byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    public byte[] decrypt(byte[] data) throws Exception {
        if (data.length < 12 + 16) throw new IllegalArgumentException("túl rövid");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, 12));
        return c.doFinal(data, 12, data.length - 12);
    }
}
