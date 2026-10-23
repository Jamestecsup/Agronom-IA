package com.agronomia.backend.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Autenticación del panel contra las tablas que crea Django
 * (`auth_user` + `core_profile`), sin duplicar usuarios.
 */
@Service
public class AdminAuthService {

    private static final String SELECT_USER = """
            SELECT u.id, u.username, u.password, u.is_active,
                   COALESCE(p.role, 'user') AS role
            FROM auth_user u
            LEFT JOIN core_profile p ON p.user_id = u.id
            WHERE u.username = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AdminTokenService tokens;

    public AdminAuthService(JdbcTemplate jdbcTemplate, AdminTokenService tokens) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokens = tokens;
    }

    /** Datos mínimos de la fila de autenticación. */
    private record AuthRow(
            long id,
            String username,
            String passwordHash,
            boolean active,
            String role
    ) {
    }

    /**
     * Valida credenciales y exige rol admin.
     *
     * @throws AdminException 400 si faltan datos, 401 si no coincide,
     *                        403 si la cuenta está desactivada o no es admin.
     */
    public AdminDtos.LoginResponse login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "Faltan usuario o contraseña.");
        }

        List<AuthRow> rows = jdbcTemplate.query(
                SELECT_USER,
                (resultSet, index) -> new AuthRow(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password"),
                        resultSet.getBoolean("is_active"),
                        resultSet.getString("role")
                ),
                username.trim()
        );
        AuthRow user = rows.stream().findFirst().orElse(null);
        if (user == null || !DjangoPasswordEncoder.matches(password, user.passwordHash())) {
            throw new AdminException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
        }
        if (!user.active()) {
            throw new AdminException(HttpStatus.FORBIDDEN, "La cuenta está desactivada.");
        }
        if (!"admin".equalsIgnoreCase(user.role())) {
            throw new AdminException(HttpStatus.FORBIDDEN,
                    "Esta cuenta no tiene rol de administrador.");
        }

        String token = tokens.issue(user.id(), user.username());
        return new AdminDtos.LoginResponse(
                true, token, user.username(), "admin", tokens.expiresAt()
        );
    }
}
