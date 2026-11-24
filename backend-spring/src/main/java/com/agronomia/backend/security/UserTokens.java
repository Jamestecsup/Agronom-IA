package com.agronomia.backend.security;

import com.agronomia.backend.images.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class UserTokens {
    private static final long TTL = 14L * 24 * 3600;
    private final SecretKeySpec key;

    public UserTokens(@Value("${USER_TOKEN_SECRET:}") String secret) {
        if (secret.length() < 24) {
            throw new IllegalStateException("Configura USER_TOKEN_SECRET con al menos 24 caracteres.");
        }
        key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String issue(long userId) {
        String payload = userId + "." + (Instant.now().getEpochSecond() + TTL);
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public long requireUserId(String authorization) {
        if (authorization != null && authorization.length() <= 256
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).strip();
            int separator = token.lastIndexOf('.');
            if (separator > 0) {
                String payload = token.substring(0, separator);
                try {
                    if (MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(token.substring(separator + 1)))) {
                        String[] parts = payload.split("\\.", -1);
                        if (parts.length == 2 && Long.parseLong(parts[1]) > Instant.now().getEpochSecond()) {
                            long id = Long.parseLong(parts[0]);
                            if (id > 0) return id;
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Un formato inválido recibe el mismo 401 que una firma inválida.
                }
            }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(("agronomia-user:" + payload).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("No se pudo firmar el token de usuario.", error);
        }
    }
}
