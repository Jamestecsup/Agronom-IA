package com.agronomia.backend.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consultas de solo lectura y gestión de usuarios para el panel.
 *
 * Lee las tablas de Django (`auth_user`, `perfiles_usuario`,
 * `identificaciones_planta`, `mensajes_chat`) y las imágenes de
 * `imagenes_planta`, todas en la misma base MySQL.
 */
@Service
public class AdminDataService {

    private static final Set<String> ROLES = Set.of("user", "admin");

    private final JdbcTemplate jdbcTemplate;

    public AdminDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean databaseUp() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public AdminDtos.StatsResponse stats() {
        long users = count("SELECT COUNT(*) FROM auth_user");
        long plants = count("SELECT COUNT(*) FROM identificaciones_planta");
        long chats = count("SELECT COUNT(*) FROM mensajes_chat");
        long images = count("SELECT COUNT(*) FROM imagenes_planta");

        List<AdminDtos.SpeciesCount> topSpecies = jdbcTemplate.query(
                """
                SELECT scientific_name, COALESCE(MAX(common_name), '') AS common_name,
                       COUNT(*) AS total
                FROM identificaciones_planta
                GROUP BY scientific_name
                ORDER BY total DESC, scientific_name
                LIMIT 5
                """,
                (resultSet, index) -> new AdminDtos.SpeciesCount(
                        resultSet.getString("scientific_name"),
                        resultSet.getString("common_name"),
                        resultSet.getLong("total")
                )
        );

        List<AdminDtos.DayCount> daily = jdbcTemplate.query(
                """
                SELECT DATE(created_at) AS day, COUNT(*) AS total
                FROM identificaciones_planta
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)
                GROUP BY DATE(created_at)
                ORDER BY day
                """,
                (resultSet, index) -> new AdminDtos.DayCount(
                        resultSet.getDate("day").toString(),
                        resultSet.getLong("total")
                )
        );

        return new AdminDtos.StatsResponse(users, plants, chats, images, topSpecies, daily);
    }

    public List<AdminDtos.UserRow> users(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String term = query == null ? "" : query.trim();
        if (term.isEmpty()) {
            return jdbcTemplate.query(
                    """
                    SELECT u.id, u.username, u.email, u.is_active, u.date_joined,
                           COALESCE(p.role, 'user') AS role
                    FROM auth_user u
                    LEFT JOIN perfiles_usuario p ON p.user_id = u.id
                    ORDER BY u.id
                    LIMIT ?
                    """,
                    this::mapUser,
                    safeLimit
            );
        }
        String like = "%" + term + "%";
        return jdbcTemplate.query(
                """
                SELECT u.id, u.username, u.email, u.is_active, u.date_joined,
                       COALESCE(p.role, 'user') AS role
                FROM auth_user u
                LEFT JOIN perfiles_usuario p ON p.user_id = u.id
                WHERE u.username LIKE ? OR u.email LIKE ?
                ORDER BY u.id
                LIMIT ?
                """,
                this::mapUser,
                like, like, safeLimit
        );
    }

    /**
     * Cambia rol y/o estado de una cuenta.
     *
     * @param actorId  administrador que hace el cambio (no puede quitarse el rol).
     */
    @Transactional
    public AdminDtos.UserRow updateUser(
            long actorId,
            long userId,
            String role,
            Boolean active
    ) {
        AdminDtos.UserRow current = findUser(userId);
        if (current == null) {
            throw new AdminException(HttpStatus.NOT_FOUND, "El usuario no existe.");
        }

        String nextRole = role == null ? current.role() : role.trim().toLowerCase(Locale.ROOT);
        boolean nextActive = active == null ? current.active() : active;

        if (!ROLES.contains(nextRole)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "Rol inválido (user|admin).");
        }
        if (actorId == userId && (!"admin".equals(nextRole) || !nextActive)) {
            throw new AdminException(HttpStatus.BAD_REQUEST,
                    "No puedes quitarte el rol de administrador ni desactivar tu cuenta.");
        }

        jdbcTemplate.update("UPDATE auth_user SET is_active = ? WHERE id = ?", nextActive, userId);
        int updated = jdbcTemplate.update(
                "UPDATE perfiles_usuario SET role = ? WHERE user_id = ?", nextRole, userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO perfiles_usuario (user_id, role) VALUES (?, ?)", userId, nextRole
            );
        }
        return findUser(userId);
    }

    public List<AdminDtos.PlantRow> plants(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
                """
                SELECT p.id, p.user_id, COALESCE(u.username, 'Sin usuario') AS username,
                       p.scientific_name, p.common_name, p.family, p.confidence, p.created_at
                FROM identificaciones_planta p
                LEFT JOIN auth_user u ON u.id = p.user_id
                ORDER BY p.created_at DESC
                LIMIT ?
                """,
                (resultSet, index) -> new AdminDtos.PlantRow(
                        resultSet.getString("id"),
                        nullableLong(resultSet.getObject("user_id")),
                        resultSet.getString("username"),
                        resultSet.getString("scientific_name"),
                        resultSet.getString("common_name"),
                        resultSet.getString("family"),
                        resultSet.getDouble("confidence"),
                        formatTimestamp(resultSet.getTimestamp("created_at"))
                ),
                safeLimit
        );
    }

    public List<AdminDtos.ChatRow> chats(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
                """
                SELECT m.id, m.user_id, COALESCE(u.username, 'Sin usuario') AS username,
                       m.session_id, m.role, m.content, m.created_at
                FROM mensajes_chat m
                LEFT JOIN auth_user u ON u.id = m.user_id
                ORDER BY m.created_at DESC
                LIMIT ?
                """,
                (resultSet, index) -> new AdminDtos.ChatRow(
                        resultSet.getString("id"),
                        nullableLong(resultSet.getObject("user_id")),
                        resultSet.getString("username"),
                        resultSet.getString("session_id"),
                        resultSet.getString("role"),
                        resultSet.getString("content"),
                        formatTimestamp(resultSet.getTimestamp("created_at"))
                ),
                safeLimit
        );
    }

    private AdminDtos.UserRow findUser(long userId) {
        List<AdminDtos.UserRow> rows = jdbcTemplate.query(
                """
                SELECT u.id, u.username, u.email, u.is_active, u.date_joined,
                       COALESCE(p.role, 'user') AS role
                FROM auth_user u
                LEFT JOIN perfiles_usuario p ON p.user_id = u.id
                WHERE u.id = ?
                """,
                this::mapUser,
                userId
        );
        return rows.stream().findFirst().orElse(null);
    }

    private AdminDtos.UserRow mapUser(java.sql.ResultSet resultSet, int index)
            throws java.sql.SQLException {
        return new AdminDtos.UserRow(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("email"),
                resultSet.getString("role"),
                resultSet.getBoolean("is_active"),
                formatTimestamp(resultSet.getTimestamp("date_joined"))
        );
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().toString();
    }
}
