package com.agronomia.backend.security;

import com.agronomia.backend.images.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class LoginAttempts {
    private static final int WINDOW_SECONDS = 300;
    private static final int CAPACITY = 10_000;
    private final Map<String, Attempts> attempts = new HashMap<>();

    public synchronized void reserve(String account, String address) {
        long now = Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry -> entry.getValue().expires <= now);
        String accountKey = "account:" + account;
        String ipKey = "ip:" + address;
        if (attempts.size() >= CAPACITY || count(accountKey) >= 5 || count(ipKey) >= 30) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Inténtalo en unos minutos.");
        }
        increment(accountKey, now);
        increment(ipKey, now);
    }

    public synchronized void accept(String account) {
        attempts.remove("account:" + account);
    }

    private int count(String key) {
        Attempts current = attempts.get(key);
        return current == null ? 0 : current.count;
    }

    private void increment(String key, long now) {
        attempts.compute(key, (ignored, old) -> old == null
                ? new Attempts(1, now + WINDOW_SECONDS) : new Attempts(old.count + 1, old.expires));
    }

    private record Attempts(int count, long expires) {}
}
