package com.agronomia.backend.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Lee y escribe el formato PBKDF2-SHA256 de auth_user.password de Django. */
@Component
public final class DjangoPasswordEncoder implements PasswordEncoder {
    private static final String ALGORITHM = "pbkdf2_sha256";
    private static final int ITERATIONS = 600_000;
    private static final int HASH_BYTES = 32;
    private static final int MAX_ITERATIONS = 10_000_000;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] randomSalt = new byte[17];
        random.nextBytes(randomSalt);
        String salt = HexFormat.of().formatHex(randomSalt);
        return ALGORITHM + "$" + ITERATIONS + "$" + salt + "$"
                + Base64.getEncoder().encodeToString(hash(rawPassword, salt, ITERATIONS));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$", 4);
        if (parts.length != 4 || !ALGORITHM.equals(parts[0]) || parts[2].isEmpty()) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return iterations > 0 && iterations <= MAX_ITERATIONS && expected.length == HASH_BYTES
                    && MessageDigest.isEqual(expected, hash(rawPassword, parts[2], iterations));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static byte[] hash(CharSequence password, String salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toString().toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8), iterations, HASH_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("No se pudo calcular PBKDF2-SHA256.", error);
        } finally {
            spec.clearPassword();
        }
    }
}
