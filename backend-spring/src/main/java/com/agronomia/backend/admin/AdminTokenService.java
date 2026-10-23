package com.agronomia.backend.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Emite y valida los tokens del panel con HMAC-SHA256.
 *
 * El token es `payload.firma` sin estado en base de datos: el servidor solo
 * comprueba la firma y la caducidad. El secreto se toma de ADMIN_TOKEN_SECRET.
 */
@Service
public class AdminTokenService {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;

    public AdminTokenService(
            @Value("${admin.token.secret:dev-only-change-me}") String secret,
            @Value("${admin.token.ttl-hours:8}") long ttlHours
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(1, ttlHours) * 3600;
    }

    /** Sesión mínima que viaja dentro del token. */
    public record AdminSession(long userId, String username) {
    }

    public String issue(long userId, String username) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = userId + "|" + username + "|" + expiresAt;
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + sign(payload);
    }

    public long expiresAt() {
        return Instant.now().getEpochSecond() + ttlSeconds;
    }

    /** @return la sesión si el token es válido y no ha caducado. */
    public Optional<AdminSession> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(
                    DECODER.decode(token.substring(0, dot)), StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
        String expected = sign(payload);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.substring(dot + 1).getBytes(StandardCharsets.UTF_8)
        )) {
            return Optional.empty();
        }

        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        long expiry;
        try {
            expiry = Long.parseLong(parts[2]);
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
        if (expiry < Instant.now().getEpochSecond()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AdminSession(Long.parseLong(parts[0]), parts[1]));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return ENCODER.encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo firmar el token", error);
        }
    }
}
