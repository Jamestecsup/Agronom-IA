package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.images.ImageValidator;
import com.agronomia.backend.schedule.ScheduleAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatScheduleContractTest {
    private static final long USER = 7L;
    private static final String BEARER = "Bearer fixture";
    private final ObjectMapper json = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserAccounts accounts = mock(UserAccounts.class);
    private final UserPlants plants = mock(UserPlants.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    @TempDir Path storage;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void chatProvidesTheLastTwelveTurnsAndRejectsExcessInput() {
        var ai = mock(AiClient.class);
        when(ai.forUser(anyLong())).thenReturn(ai);
        when(ai.inputBudget()).thenReturn(Integer.MAX_VALUE);
        var chat = chat(ai);
        when(jdbc.query(contains("LIMIT 12"), any(RowMapper.class), eq(USER), eq("default")))
                .thenReturn(new ArrayList<>(java.util.stream.IntStream.iterate(12, i -> i - 1).limit(12)
                        .mapToObj(i -> AiClient.message("user", "mensaje " + i)).toList()));
        when(plants.plants(USER)).thenReturn(List.of());
        when(ai.reply(eq("chat"), anyList(), eq(false))).thenAnswer(call -> {
            List<Map<String, Object>> turns = call.getArgument(1);
            assertThat(turns).hasSize(14);
            assertThat(turns.get(1).toString()).contains("mensaje 1");
            assertThat(turns.get(12).toString()).contains("mensaje 12");
            assertThat(turns.get(13).toString()).contains("pregunta nueva");
            return "Respuesta sintética.";
        });
        assertThat(chat.chat(BEARER, "pregunta nueva", "default", "", "", null))
                .containsEntry("reply", "Respuesta sintética.");
        assertThatThrownBy(() -> chat.chat(BEARER, "a".repeat(4001), "default", "", "", null))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(ai, times(1)).reply(eq("chat"), anyList(), eq(false));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void photoWithoutVisionReturns502WithoutPersistingAnyTurns() throws Exception {
        var ai = mock(AiClient.class);
        when(ai.forUser(anyLong())).thenReturn(ai);
        when(ai.inputBudget()).thenReturn(Integer.MAX_VALUE);
        when(ai.supportsVision()).thenReturn(true);
        var chat = chat(ai);
        var plant = plantFixture();
        when(plants.requirePlant(USER, plant.publicId())).thenReturn(plant);
        when(jdbc.query(contains("LIMIT 12"), any(RowMapper.class), eq(USER), eq(plant.sessionId())))
                .thenReturn(new ArrayList<>());
        when(ai.reply(eq("chat"), anyList(), eq(true))).thenAnswer(call -> {
            List<Map<String, Object>> turns = call.getArgument(1);
            assertThat(turns.get(0).toString()).doesNotContain("image_url");
            assertThat(turns.get(1).toString()).contains("image_url");
            throw new AiClient.Failure("El modelo configurado no admite imágenes.", true);
        });
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB), "png", image);
        var file = new MockMultipartFile("image", "foto.png", "image/png", image.toByteArray());
        assertThatThrownBy(() -> chat.chat(BEARER, "", "default", "", plant.publicId(), file))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(error.getMessage()).contains("no admite imágenes");
                });
        verifyNoInteractions(tx);
        try (var paths = Files.list(storage)) {
            assertThat(paths.count()).isZero();
        }
    }

    @Test
    void listsPlantChatsFromTheCurrentUsersRemainingMessages() {
        var chat = chat(mock(AiClient.class));
        var plant = plantFixture();
        when(jdbc.queryForList("SELECT DISTINCT session_id FROM mensajes_chat WHERE user_id=?", String.class, USER))
                .thenReturn(List.of("default", plant.sessionId()), List.of());
        assertThat(chat.conversations(BEARER)).containsEntry("plant_ids", List.of(plant.publicId()));
        assertThat(chat.conversations(BEARER)).containsEntry("plant_ids", List.of());
    }

    @Test
    void deletesOnlyOwnedChatAndItsFilesAndCanRepeat() throws Exception {
        var chat = chat(mock(AiClient.class));
        var plant = plantFixture();
        when(plants.requirePlant(USER, plant.publicId())).thenReturn(plant);
        String relative = "progress/" + plant.publicId() + "/" + UUID.randomUUID() + ".jpg";
        Path photo = storage.resolve(relative);
        Files.createDirectories(photo.getParent());
        Files.write(photo, new byte[]{1});
        when(jdbc.queryForList(contains("WHERE user_id=? AND plant_id=?"), eq(String.class), eq(USER), eq(plant.id())))
                .thenReturn(List.of(relative), List.of());
        assertThat(chat.deleteChat(BEARER, plant.publicId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(photo).doesNotExist();
        assertThat(chat.deleteChat(BEARER, plant.publicId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(jdbc, times(2)).update(contains("DELETE FROM mensajes_chat WHERE user_id=? AND session_id=?"), eq(USER), eq(plant.sessionId()));
        assertThatThrownBy(() -> chat.deleteAllChats(BEARER, false)).isInstanceOf(ApiException.class);
    }

    @Test
    void expiredImportUsesFortyEightHoursAndDeletesTheTemporaryFile() throws Exception {
        var schedules = schedule(mock(ScheduleAiService.class));
        var fixture = pending(true);
        assertThatThrownBy(() -> schedules.importInfo(BEARER, fixture.id))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.GONE));
        assertThat(fixture.pdf).doesNotExist();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void confirmsScheduleAndDiscardsCareThatOverlapsClass() throws Exception {
        var engine = mock(ScheduleAiService.class);
        var schedules = schedule(engine);
        var fixture = pending(false);
        var classes = classes();
        assertThat(schedules.confirm(BEARER, fixture.id, json.createObjectNode().set("classes", classes))
                .path("pdf_removed").asBoolean()).isTrue();
        assertThat(fixture.pdf).doesNotExist();
        verify(jdbc).update(contains("INSERT INTO horarios_semanales"), eq(USER), anyString());

        var plant = plantFixture();
        when(jdbc.query(contains("SELECT classes FROM horarios_semanales"), any(RowMapper.class), eq(USER)))
                .thenReturn(List.of(classes));
        when(plants.plants(USER)).thenReturn(List.of(plant));
        var response = json.createObjectNode();
        var tasks = response.putArray("tasks");
        tasks.addObject().put("plant_id", plant.publicId()).put("day_of_week", 1)
                .put("title", "No válido").put("start_time", "08:30").put("duration_minutes", 20);
        tasks.addObject().put("plant_id", plant.publicId()).put("day_of_week", 1)
                .put("title", "Válido").put("start_time", "10:00").put("duration_minutes", 20);
        when(engine.draftCarePlan(any(), anyLong())).thenReturn(response);
        var drafted = schedules.draft(BEARER).path("tasks");
        assertThat(drafted.size()).isEqualTo(1);
        assertThat(drafted.get(0).path("title").asText()).isEqualTo("Válido");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ImportFixture pending(boolean expired) throws Exception {
        String id = UUID.randomUUID().toString();
        String key = UserPlants.databaseId(id);
        String path = "schedules/pending/prueba.pdf";
        Path file = storage.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "%PDF-sintético");
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(key);
        when(row.getString(2)).thenReturn(path);
        when(row.getString(3)).thenReturn(classes().toString());
        when(row.getBoolean(4)).thenReturn(expired);
        when(plants.decode(classes().toString())).thenReturn(classes());
        when(jdbc.query(contains("INTERVAL 48 HOUR"), any(RowMapper.class), eq(USER), eq(key)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
        when(jdbc.update(contains("DELETE FROM importaciones_horario_pendientes WHERE id=?"), eq(key), eq(USER)))
                .thenReturn(1);
        return new ImportFixture(id, file);
    }

    private com.fasterxml.jackson.databind.JsonNode classes() {
        var rows = json.createArrayNode();
        rows.addObject().put("course_name", "Clase de prueba").put("day_of_week", 1)
                .put("start_time", "08:00").put("end_time", "09:00");
        return rows;
    }

    private UserPlants.StoredPlant plantFixture() {
        return new UserPlants.StoredPlant(UserPlants.newId(), "lote", "Helianthus annuus", "girasol",
                "Asteraceae", 0.7, "", json.createObjectNode());
    }

    private void bind() {
        when(accounts.requireUser(BEARER)).thenReturn(USER);
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    private UserChatController chat(AiClient ai) {
        bind();
        var cards = mock(PlantCards.class);
        when(cards.skill("chat-assistant")).thenReturn("Responde en español.");
        return new UserChatController(accounts, plants, cards, new UserFiles(storage.toString()),
                new ImageValidator(), jdbc, json, ai, tx);
    }

    private UserScheduleController schedule(ScheduleAiService engine) {
        bind();
        return new UserScheduleController(accounts, plants, new UserFiles(storage.toString()), engine, jdbc, json, tx);
    }

    private record ImportFixture(String id, Path pdf) {}
}
