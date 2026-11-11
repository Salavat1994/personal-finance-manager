package ru.homework.finance.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/** PBKDF2 с индивидуальной солью; пароли в открытом виде не сохраняются. */
public final class PasswordService {
    private static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private final SecureRandom random = new SecureRandom();

    public PasswordHash hash(char[] password) {
        validatePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt);
        try {
            return new PasswordHash(Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(derived));
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    public boolean verify(char[] password, String encodedSalt, String encodedHash) {
        if (password == null || encodedSalt == null || encodedHash == null) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            byte[] expected = Base64.getDecoder().decode(encodedHash);
            if (salt.length != SALT_BYTES || expected.length != HASH_BITS / 8) {
                return false;
            }
            byte[] actual = derive(password, salt);
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 8 || password.length > 128) {
            throw new IllegalArgumentException("Пароль должен содержать от 8 до 128 символов.");
        }
    }

    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Алгоритм защиты паролей недоступен.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public record PasswordHash(String salt, String hash) {
    }
}
