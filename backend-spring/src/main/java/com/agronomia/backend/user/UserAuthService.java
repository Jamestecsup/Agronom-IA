package com.agronomia.backend.user;

import com.agronomia.backend.admin.DjangoPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAuthService {
    private final JdbcTemplate jdbc;
    private final UserTokenService tokens;

    public UserAuthService(JdbcTemplate jdbc, UserTokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    private record Row(long id, String username, String password, boolean active, String role) {}

    public UserDtos.AuthResponse login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new UserApiException(HttpStatus.BAD_REQUEST, "Faltan username o password.");
        }
        List<Row> rows = jdbc.query(
                "SELECT u.id, u.username, u.password, u.is_active, COALESCE(p.role,'user') AS role " +
                        "FROM auth_user u LEFT JOIN perfiles_usuario p ON p.user_id=u.id WHERE u.username=?",
                (rs, i) -> new Row(rs.getLong("id"), rs.getString("username"), rs.getString("password"), rs.getBoolean("is_active"), rs.getString("role")),
                username.trim());
        Row user = rows.stream().findFirst().orElse(null);
        if (user == null || !DjangoPasswordEncoder.matches(password, user.password())) {
            throw new UserApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
        }
        if (!user.active()) {
            throw new UserApiException(HttpStatus.FORBIDDEN, "La cuenta está desactivada.");
        }
        String token = tokens.issue(user.id(), user.username());
        return new UserDtos.AuthResponse(true, user.username(), user.role(), token, null);
    }

    @Transactional
    public UserDtos.AuthResponse register(String username, String password, String email) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new UserApiException(HttpStatus.BAD_REQUEST, "Faltan username o password.");
        }
        String u = username.trim();
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM auth_user WHERE username=?", Integer.class, u);
        if (exists != null && exists > 0) {
            throw new UserApiException(HttpStatus.CONFLICT, "El usuario ya existe.");
        }
        String hash = djangoHash(password);
        jdbc.update("INSERT INTO auth_user (password,is_superuser,username,first_name,last_name,email,is_staff,is_active,date_joined) VALUES (?,?,?,?,?,?,?,?,NOW())",
                hash, false, u, "", "", email == null ? "" : email.trim(), false, true);
        Long id = jdbc.queryForObject("SELECT id FROM auth_user WHERE username=?", Long.class, u);
        jdbc.update("INSERT INTO perfiles_usuario (user_id, role) VALUES (?, 'user')", id);
        return new UserDtos.AuthResponse(true, u, "user", null, null);
    }

    /** Genera hash pbkdf2_sha256 compatible con Django. */
    private String djangoHash(String raw) {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        String saltB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                .replace('-', 'A').replace('_', 'B').substring(0, 16);
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(raw.toCharArray(), saltB64.getBytes(java.nio.charset.StandardCharsets.UTF_8), 600000, 256);
            byte[] dk = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return "pbkdf2_sha256$600000$" + saltB64 + "$" + java.util.Base64.getEncoder().encodeToString(dk);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el hash de contraseña", e);
        }
    }
}
