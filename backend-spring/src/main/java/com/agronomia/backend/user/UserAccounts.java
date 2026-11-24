package com.agronomia.backend.user;

import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.security.LoginAttempts;
import com.agronomia.backend.security.UserTokens;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class UserAccounts {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final UserTokens tokens;
    private final LoginAttempts attempts;
    private final String dummyHash;

    public UserAccounts(JdbcTemplate jdbc, PasswordEncoder passwords, UserTokens tokens, LoginAttempts attempts) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
        this.attempts = attempts;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public Map<String, Object> register(Credentials input, String remoteAddress) {
        attempts.reserveRegistration(remoteAddress);
        String username = clean(input.username());
        String password = clean(input.password());
        String email = clean(input.email());
        if (username.isEmpty() || password.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Faltan username o password.");
        }
        if (password.codePointCount(0, password.length()) < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos ocho caracteres.");
        }
        if (username.length() > 150 || email.length() > 254 || password.length() > 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Los datos de la cuenta exceden la longitud permitida.");
        }
        try {
            jdbc.update("""
                    INSERT INTO auth_user
                    (username, password, email, first_name, last_name, is_active, is_staff, is_superuser, date_joined)
                    VALUES (?, ?, ?, '', '', TRUE, FALSE, FALSE, UTC_TIMESTAMP(6))
                    """, username, passwords.encode(password), email);
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "El usuario ya existe.");
        }
        Long user = jdbc.queryForObject("SELECT id FROM auth_user WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO perfiles_usuario (user_id, role) VALUES (?, 'user')", user);
        return Map.of("success", true, "username", username, "role", "user");
    }

    public Map<String, Object> login(Credentials input, String remoteAddress) {
        String username = clean(input.username());
        String password = clean(input.password());
        if (username.length() > 150 || password.length() > 1024) throw invalidCredentials();
        String account = username.toLowerCase(Locale.ROOT);
        attempts.reserve(account, remoteAddress);
        Account row = jdbc.query("""
                SELECT u.id, u.username, u.password, u.is_active, COALESCE(p.role, 'user') role
                FROM auth_user u LEFT JOIN perfiles_usuario p ON p.user_id=u.id WHERE u.username=?
                """, (rs, index) -> new Account(rs.getLong("id"), rs.getString("username"),
                rs.getString("password"), rs.getBoolean("is_active"), rs.getString("role")), username)
                .stream().findFirst().orElse(null);
        boolean valid = passwords.matches(password, row == null ? dummyHash : row.hash);
        if (row == null || !row.active || !valid) throw invalidCredentials();
        attempts.accept(account);
        return Map.of("success", true, "username", row.username, "role", row.role, "token", tokens.issue(row.id));
    }

    public long requireUser(String authorization) {
        long user = tokens.requireUserId(authorization);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM auth_user WHERE id=? AND is_active=TRUE", Integer.class, user);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        return user;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private record Account(long id, String username, String hash, boolean active, String role) {}
    public record Credentials(String username, String password, String email) {}
}
