package com.securebank.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PINs are never stored in plain text: PBKDF2-HMAC-SHA256 + random salt. */
public final class PinHasher {
    private static final int ITERATIONS = 60_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHasher() { }

    public static boolean isValidFormat(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String pin, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PIN hashing is not available", e);
        }
    }

    public static boolean matches(String pin, String salt, String expectedHash) {
        if (!isValidFormat(pin)) return false;
        return MessageDigest.isEqual(hash(pin, salt).getBytes(StandardCharsets.UTF_8),
                                     expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
