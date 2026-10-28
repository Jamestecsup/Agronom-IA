package com.agronomia.backend.admin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Verifica contraseñas creadas por Django.
 *
 * Django guarda `pbkdf2_sha256$iteraciones$salt$hash` donde el hash está en
 * Base64. Se valida con el mismo algoritmo para no duplicar usuarios ni
 * cambiar el formato que ya usa el servicio de usuario (Django).
 */
public final class DjangoPasswordEncoder {

    private static final String ALGORITHM = "pbkdf2_sha256";

    private DjangoPasswordEncoder() {
    }

    /**
     * @param rawPassword contraseña escrita por el administrador.
     * @param encoded     valor de `auth_user.password`.
     * @return true si coinciden.
     */
    public static boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null || encoded.isBlank()) {
            return false;
        }
        String[] parts = encoded.split("\\$", 4);
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return false;
        }

        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException error) {
            return false;
        }
        byte[] salt = parts[2].getBytes(StandardCharsets.UTF_8);
        byte[] expected;
        try {
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (expected.length == 0) {
            return false;
        }

        PBEKeySpec spec = new PBEKeySpec(
                rawPassword.toCharArray(), salt, iterations, expected.length * 8
        );
        try {
            byte[] actual = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception error) {
            return false;
        } finally {
            spec.clearPassword();
        }
    }
}
