package com.itheima.storage;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private static final int ITERATIONS = 210000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    public static boolean isValidHash(String encoded) {
        try {
            String[] parts = encoded.split("\\$", -1);
            return parts.length == 4 && parts[0].equals("pbkdf2")
                    && Integer.parseInt(parts[1]) == ITERATIONS
                    && Base64.getDecoder().decode(parts[2]).length == 16
                    && Base64.getDecoder().decode(parts[3]).length == 32;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean verify(String password, String encoded) {
        if (!isValidHash(encoded)) return false;
        String[] parts = encoded.split("\\$");
        byte[] actual = derive(password, Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]), actual);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
