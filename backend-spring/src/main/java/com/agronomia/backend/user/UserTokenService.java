package com.agronomia.backend.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.Inflater;

/**
 * Token firmado para usuarios (web/app movil).
 * Verifica tokens Spring HMAC propios y, opcionalmente, tokens legacy de Django
 * signing (salt agronomia.user-api) para no cortar sesiones existentes.
 */
@Service
public class UserTokenService {
    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder DJANGO_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DJANGO_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;
    private final String djangoSecretKey;
    private final String djangoSalt;
    private final long djangoMaxAgeSeconds;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public UserTokenService(
            @Value("${admin.token.secret:dev-only-change-me}") String secret,
            @Value("${user.token.ttl-hours:336}") long ttlHours,
            @Value("${django.auth.secret-key:${DJANGO_SECRET_KEY:}}") String djangoSecretKey,
            @Value("${django.auth.salt:agronomia.user-api}") String djangoSalt,
            @Value("${django.auth.max-age-seconds:1209600}") long djangoMaxAgeSeconds,
            JdbcTemplate jdbc,
            ObjectMapper json
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(1, ttlHours) * 3600;
        this.djangoSecretKey = djangoSecretKey == null ? "" : djangoSecretKey;
        this.djangoSalt = djangoSalt == null ? "agronomia.user-api" : djangoSalt;
        this.djangoMaxAgeSeconds = Math.max(1, djangoMaxAgeSeconds);
        this.jdbc = jdbc;
        this.json = json;
    }

    public record UserSession(long userId, String username) {}

    public String issue(long userId, String username) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = userId + "|" + username + "|" + expiresAt;
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
    }

    public Optional<UserSession> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String t = token.trim();
        Optional<UserSession> spring = verifySpring(t);
        if (spring.isPresent()) return spring;
        return verifyLegacyDjango(t);
    }

    private Optional<UserSession> verifySpring(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        String payload;
        try {
            payload = new String(DECODER.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String expected = sign(payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                token.substring(dot + 1).getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) return Optional.empty();
        try {
            long expiry = Long.parseLong(parts[2]);
            if (expiry < Instant.now().getEpochSecond()) return Optional.empty();
            return Optional.of(new UserSession(Long.parseLong(parts[0]), parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<UserSession> verifyLegacyDjango(String token) {
        if (djangoSecretKey.isBlank()) return Optional.empty();
        String[] parts = token.split(":");
        if (parts.length != 3) return Optional.empty();
        String payloadB64 = parts[0];
        String timestampB62 = parts[1];
        String sig = parts[2];
        if (payloadB64.isBlank() || timestampB62.isBlank() || sig.isBlank()) return Optional.empty();
        String value = payloadB64 + ":" + timestampB62;
        String expected = djangoBase64Hmac(djangoSalt + "signer", value, djangoSecretKey);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            long ts = base62Decode(timestampB62);
            if (Instant.now().getEpochSecond() - ts > djangoMaxAgeSeconds) return Optional.empty();
            byte[] raw = DJANGO_DECODER.decode(payloadB64);
            if (raw.length > 0 && raw[0] == '.') {
                raw = zlibDecompress(raw, 1, raw.length - 1);
            }
            JsonNode node = json.readTree(raw);
            long userId = node.path("user_id").asLong(0);
            if (userId <= 0) return Optional.empty();
            String username = jdbc.query("SELECT username FROM auth_user WHERE id=? AND is_active=1 LIMIT 1",
                    rs -> rs.next() ? rs.getString("username") : null, userId);
            if (username == null || username.isBlank()) return Optional.empty();
            return Optional.of(new UserSession(userId, username));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String djangoBase64Hmac(String salt, String value, String key) {
        try {
            // Django salted_hmac: derived_key = sha256(salt + secret).digest(); hmac_sha256(derived_key, value)
            byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[saltBytes.length + keyBytes.length];
            System.arraycopy(saltBytes, 0, combined, 0, saltBytes.length);
            System.arraycopy(keyBytes, 0, combined, saltBytes.length, keyBytes.length);
            byte[] derivedKey = sha256(combined);
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(derivedKey, HMAC));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return DJANGO_ENCODER.encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo verificar token legacy", e);
        }
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static long base62Decode(String value) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            int idx = chars.indexOf(value.charAt(i));
            if (idx < 0) throw new IllegalArgumentException("bad base62");
            result = result * 62 + idx;
        }
        return result;
    }

    private static byte[] zlibDecompress(byte[] data, int offset, int len) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data, offset, len);
        byte[] buf = new byte[Math.max(256, len * 4)];
        int out = 0;
        while (!inflater.finished() && !inflater.needsInput()) {
            if (out == buf.length) {
                byte[] bigger = new byte[buf.length * 2];
                System.arraycopy(buf, 0, bigger, 0, out);
                buf = bigger;
            }
            int n = inflater.inflate(buf, out, buf.length - out);
            if (n == 0) break;
            out += n;
        }
        inflater.end();
        byte[] result = new byte[out];
        System.arraycopy(buf, 0, result, 0, out);
        return result;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }
}
