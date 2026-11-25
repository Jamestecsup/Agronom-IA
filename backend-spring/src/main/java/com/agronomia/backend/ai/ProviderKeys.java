package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
final class ProviderKeys {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKeySpec> keys = new HashMap<>();
    private final String current;

    ProviderKeys(ObjectMapper mapper, @Value("${AI_KEYS_MASTER_KEY:}") String key,
                 @Value("${AI_KEYS_KID:v1}") String current,
                 @Value("${AI_KEYS_PREVIOUS:}") String previous) {
        this.current = current;
        try {
            if (!previous.isBlank() && !previous.equals("{}")) {
                mapper.readValue(previous, new TypeReference<Map<String, String>>() {}).forEach(this::add);
            }
            if (!key.isBlank()) add(current, key);
        } catch (Exception failure) {
            throw new IllegalStateException("Configuración de cifrado de proveedores inválida.");
        }
    }

    private void add(String id, String encoded) {
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length != 32 || !id.matches("[a-zA-Z0-9_-]{1,40}") || keys.containsKey(id)) {
            throw new IllegalArgumentException();
        }
        keys.put(id, new SecretKeySpec(raw, "AES"));
        java.util.Arrays.fill(raw, (byte) 0);
    }

    Secret encrypt(long owner, String id, String value) {
        if (!keys.containsKey(current)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Falta configurar el cifrado de proveedores.");
        }
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        return new Secret(nonce, crypt(Cipher.ENCRYPT_MODE, current, owner, id, nonce,
                value.getBytes(StandardCharsets.UTF_8)), current, value.substring(value.length() - 4));
    }

    String decrypt(long owner, String id, Secret secret) {
        byte[] plain = crypt(Cipher.DECRYPT_MODE, secret.kid(), owner, id, secret.nonce(), secret.value());
        try {
            return new String(plain, StandardCharsets.UTF_8);
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    private byte[] crypt(int mode, String kid, long owner, String id, byte[] nonce, byte[] value) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, keys.get(kid), new GCMParameterSpec(128, nonce));
            cipher.updateAAD((owner + ":" + id + ":" + kid).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo usar la credencial del proveedor.");
        }
    }

    record Secret(byte[] nonce, byte[] value, String kid, String suffix) {}
}
