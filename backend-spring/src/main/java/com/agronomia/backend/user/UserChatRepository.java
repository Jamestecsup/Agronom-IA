package com.agronomia.backend.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL y mapeo JDBC de mensajes e imágenes de progreso del chat. */
@Repository
public class UserChatRepository {
    private final JdbcTemplate jdbc;

    public UserChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChatHistoryRow> findHistory(long userId, String sessionId) {
        return jdbc.query(
                "SELECT m.id,m.role,m.content,m.created_at," +
                        "(SELECT pi.id FROM chat_progress_images pi WHERE pi.message_id=m.id LIMIT 1) AS progress_id " +
                        "FROM chat_messages m WHERE m.user_id=? AND m.session_id=? ORDER BY m.created_at ASC",
                (rs, rowNum) -> new ChatHistoryRow(
                        rs.getString("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime().toString(),
                        rs.getString("progress_id")
                ),
                userId, sessionId
        );
    }

    public List<PromptMessageRow> findPromptHistory(long userId, String sessionId) {
        return jdbc.query(
                "SELECT role,content FROM chat_messages WHERE user_id=? AND session_id=? ORDER BY created_at ASC",
                (rs, rowNum) -> new PromptMessageRow(rs.getString("role"), rs.getString("content")),
                userId, sessionId
        );
    }

    public String insertMessage(long userId, String sessionId, String role, String content) {
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO chat_messages (id,user_id,session_id,role,content,created_at) VALUES (?,?,?,?,?,?)",
                id,
                userId,
                sessionId,
                role,
                content,
                Timestamp.valueOf(LocalDateTime.now())
        );
        return id;
    }

    public List<ProgressImageRow> findProgressImages(long userId, String plantId) {
        return jdbc.query(
                "SELECT id,caption,created_at FROM chat_progress_images WHERE user_id=? AND plant_id=? ORDER BY created_at ASC",
                (rs, rowNum) -> new ProgressImageRow(
                        rs.getString("id"),
                        rs.getString("caption"),
                        rs.getTimestamp("created_at").toLocalDateTime().toString()
                ),
                userId, toDatabaseId(plantId)
        );
    }

    public Optional<StoredProgressImage> findProgressImage(long userId, String imageId) {
        List<StoredProgressImage> rows = jdbc.query(
                "SELECT relative_path,mime_type FROM chat_progress_images WHERE id=? AND user_id=?",
                (rs, rowNum) -> new StoredProgressImage(
                        rs.getString("relative_path"), rs.getString("mime_type")),
                toDatabaseId(imageId), userId
        );
        return rows.stream().findFirst();
    }

    public void insertProgressImage(
            long userId,
            String plantId,
            String messageId,
            String relativePath,
            String mimeType,
            long fileSize,
            String caption
    ) {
        jdbc.update(
                "INSERT INTO chat_progress_images (id,user_id,plant_id,message_id,relative_path,mime_type,file_size,caption,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString().replace("-", ""),
                userId,
                toDatabaseId(plantId),
                toDatabaseId(messageId),
                relativePath,
                mimeType,
                fileSize,
                caption == null ? "" : caption,
                Timestamp.valueOf(LocalDateTime.now())
        );
    }

    private String toDatabaseId(String id) {
        if (id == null) return null;
        String hex = id.replace("-", "");
        return hex.length() == 32 ? hex : id;
    }

    public record ChatHistoryRow(
            String id, String role, String content, String createdAt, String progressImageId) {
    }

    public record PromptMessageRow(String role, String content) {
    }

    public record ProgressImageRow(String id, String caption, String createdAt) {
    }

    public record StoredProgressImage(String relativePath, String mimeType) {
    }
}
