package com.agronomia.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserChatRepositoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserChatRepository repository;

    @Test
    void findHistoryMapsMessagesChronologicallyAndIncludesProgressImageId() {
        insertMessage("message-1", 7, "plant-plant-1", "user", "Hola", "2026-01-01T10:00:00");
        insertMessage("message-2", 7, "plant-plant-1", "assistant", "Respuesta", "2026-01-01T10:01:00");
        insertMessage("message-other-user", 8, "plant-plant-1", "user", "Privado", "2026-01-01T10:02:00");
        insertProgressImage("progress-1", 7, "00000000000000000000000000000001", "message-1",
                "progress/one.jpg", "image/jpeg", 10, "hoja", "2026-01-01T10:00:30");

        List<UserChatRepository.ChatHistoryRow> rows = repository.findHistory(7, "plant-plant-1");

        assertThat(rows).containsExactly(
                new UserChatRepository.ChatHistoryRow("message-1", "user", "Hola",
                        "2026-01-01T10:00", "progress-1"),
                new UserChatRepository.ChatHistoryRow("message-2", "assistant", "Respuesta",
                        "2026-01-01T10:01", null)
        );
    }

    @Test
    void findPromptHistoryIsScopedToOwnerAndSession() {
        insertMessage("prompt-1", 7, "plant-plant-1", "user", "Pregunta", "2026-01-01T10:00:00");
        insertMessage("prompt-2", 7, "plant-plant-1", "assistant", "Respuesta", "2026-01-01T10:01:00");
        insertMessage("prompt-other-session", 7, "plant-plant-2", "user", "Otra planta", "2026-01-01T10:02:00");
        insertMessage("prompt-other-owner", 8, "plant-plant-1", "user", "Otro usuario", "2026-01-01T10:03:00");

        assertThat(repository.findPromptHistory(7, "plant-plant-1"))
                .containsExactly(
                        new UserChatRepository.PromptMessageRow("user", "Pregunta"),
                        new UserChatRepository.PromptMessageRow("assistant", "Respuesta")
                );
    }

    @Test
    void insertMessagePersistsConversationFieldsAndReturnsItsId() {
        String id = repository.insertMessage(9, "plant-plant-9", "assistant", "Respuesta guardada");

        assertThat(id).hasSize(32);
        assertThat(jdbc.queryForMap("SELECT user_id,session_id,role,content FROM chat_messages WHERE id=?", id))
                .containsEntry("USER_ID", 9L)
                .containsEntry("SESSION_ID", "plant-plant-9")
                .containsEntry("ROLE", "assistant")
                .containsEntry("CONTENT", "Respuesta guardada");
    }

    @Test
    void progressImageQueriesNormalizePlantAndImageIdsAndEnforceOwner() {
        String dashedPlantId = "12345678-1234-1234-1234-123456789abc";
        String storedPlantId = "12345678123412341234123456789abc";
        insertProgressImage("12345678123412341234123456789def", 7, storedPlantId, "message-1",
                "progress/owned.png", "image/png", 12, "avance", "2026-01-01T10:00:00");
        insertProgressImage("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 8, storedPlantId, "message-2",
                "progress/other.png", "image/png", 12, "privado", "2026-01-01T10:01:00");

        assertThat(repository.findProgressImages(7, dashedPlantId))
                .containsExactly(new UserChatRepository.ProgressImageRow(
                        "12345678123412341234123456789def", "avance", "2026-01-01T10:00"));
        assertThat(repository.findProgressImage(7, "12345678-1234-1234-1234-123456789def"))
                .contains(new UserChatRepository.StoredProgressImage("progress/owned.png", "image/png"));
        assertThat(repository.findProgressImage(8, "12345678-1234-1234-1234-123456789def"))
                .isEmpty();
    }

    @Test
    void insertProgressImageNormalizesPlantAndMessageIds() {
        String plantId = "12345678-1234-1234-1234-123456789abc";
        String messageId = "abcdefab-cdef-abcd-efab-cdefabcdefab";

        repository.insertProgressImage(7, plantId, messageId, "progress/new.jpg", "image/jpeg", 42, "nota");

        assertThat(jdbc.queryForMap(
                "SELECT user_id,plant_id,message_id,relative_path,mime_type,file_size,caption " +
                        "FROM chat_progress_images WHERE relative_path=?", "progress/new.jpg"))
                .containsEntry("USER_ID", 7L)
                .containsEntry("PLANT_ID", "12345678123412341234123456789abc")
                .containsEntry("MESSAGE_ID", "abcdefabcdefabcdefabcdefabcdefab")
                .containsEntry("MIME_TYPE", "image/jpeg")
                .containsEntry("FILE_SIZE", 42L)
                .containsEntry("CAPTION", "nota");
    }

    private void insertMessage(String id, long userId, String sessionId, String role, String content, String createdAt) {
        jdbc.update("INSERT INTO chat_messages (id,user_id,session_id,role,content,created_at) VALUES (?,?,?,?,?,?)",
                id, userId, sessionId, role, content, timestamp(createdAt));
    }

    private void insertProgressImage(
            String id, long userId, String plantId, String messageId, String path,
            String mime, long fileSize, String caption, String createdAt
    ) {
        jdbc.update("INSERT INTO chat_progress_images " +
                        "(id,user_id,plant_id,message_id,relative_path,mime_type,file_size,caption,created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                id, userId, plantId, messageId, path, mime, fileSize, caption, timestamp(createdAt));
    }

    private Timestamp timestamp(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value));
    }
}
