package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.agronomia.backend.images.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPlants {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlantCards cards;
    private final PlantNetClient plantNet;
    private final UserFiles files;
    private final TransactionTemplate tx;

    public UserPlants(JdbcTemplate jdbc, ObjectMapper json, PlantCards cards, PlantNetClient plantNet,
                      UserFiles files, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.cards = cards;
        this.plantNet = plantNet;
        this.files = files;
        this.tx = new TransactionTemplate(txManager);
    }

    public ObjectNode identify(long user, String batch, String organ) {
        var deadline = AiClient.Deadline.after(java.time.Duration.ofSeconds(150));
        batch = batch.strip();
        if (batch.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Falta batch_id.");
        if (batch.length() > 36 || organ.length() > 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lote u órgano inválido.");
        }
        List<PlantNetClient.Photo> photos = jdbc.query("""
                SELECT relative_path,mime_type FROM imagenes_planta WHERE batch_id=? AND user_id=?
                ORDER BY created_at,id
                """, (rs, i) -> new PlantNetClient.Photo(files.existing(rs.getString(1)),rs.getString(2)), batch, user);
        if (photos.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "No se encontraron imágenes para ese batch_id.");
        JsonNode raw = json.createObjectNode();
        ObjectNode result;
        try {
            raw = plantNet.identify(photos, organ.isBlank() ? "auto" : organ.strip(), deadline);
        } catch (ApiException error) {
            saveRun(user, batch, organ, "failed", raw);
            throw error;
        }
        List<JsonNode> ranked = cards.ranked(raw);
        if (ranked.isEmpty() || ranked.get(0).path("score").asDouble() < 0.20) {
            result = state("unidentified").put("message", ranked.isEmpty() ? "No se encontraron coincidencias."
                    : "La coincidencia es muy baja. Toma mejores fotos e inténtalo de nuevo.");
        } else if (ranked.get(0).path("score").asDouble() < 0.50) {
            result = state("ambiguous");
            result.set("candidates", cards.candidates(ranked, true, deadline));
        } else {
            try {
                result = state("identified");
                result.set("plant", cards.generate(ranked.get(0), deadline));
                result.set("candidates", cards.candidates(ranked, false, deadline));
            } catch (PlantCards.NoPlantInformation ignored) {
                ObjectNode header = cards.header(ranked.get(0));
                String common = header.path("common_name").asText();
                result = insufficient(common.isBlank() ? header.path("scientific_name").asText() : common);
            } catch (AiClient.Failure error) {
                saveRun(user, batch, organ, "failed", raw);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "No pudimos preparar la ficha de esa planta. " + error.getMessage());
            }
        }
        ObjectNode response = result;
        JsonNode savedRaw = raw;
        String savedBatch = batch;
        tx.executeWithoutResult(s -> {
            saveRun(user, savedBatch, organ, response.path("status").asText(), savedRaw);
            if (response.has("plant")) savePlant(user, savedBatch, response.path("plant"));
        });
        return response;
    }

    public ObjectNode confirm(long user, String batch, String scientific) {
        var deadline = AiClient.Deadline.after(java.time.Duration.ofSeconds(150));
        if (batch.isBlank() || scientific.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Faltan batch_id o scientific_name.");
        }
        JsonNode row = jdbc.query("""
                SELECT raw_json FROM procesos_identificacion WHERE batch_id=? AND user_id=?
                ORDER BY created_at DESC,id DESC LIMIT 1
                """, (rs, i) -> decode(rs.getString(1)), batch.strip(), user)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No hay una identificación previa para ese batch_id."));
        JsonNode selected = cards.ranked(row).stream()
                .filter(candidate -> cards.scientific(candidate).strip().equalsIgnoreCase(scientific.strip()))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "La especie elegida no está entre las candidatas de ese lote."));
        try {
            JsonNode plant = cards.generate(selected, deadline);
            savePlant(user, batch.strip(), plant);
            ObjectNode answer = state("identified");
            answer.set("plant", plant);
            return answer;
        } catch (PlantCards.NoPlantInformation ignored) {
            return insufficient(scientific.strip());
        } catch (AiClient.Failure error) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No pudimos preparar la ficha de esa planta. " + error.getMessage());
        }
    }

    public List<StoredPlant> plants(long user) {
        return jdbc.query("SELECT * FROM identificaciones_planta WHERE user_id=? ORDER BY created_at DESC,id DESC",
                (rs, i) -> mapPlant(rs), user);
    }

    public StoredPlant requirePlant(long user, String publicId) {
        String key = databaseId(publicId);
        return jdbc.query("SELECT * FROM identificaciones_planta WHERE id=? AND user_id=?",
                (rs, i) -> mapPlant(rs), key, user)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No se encontró esa planta en tu cuenta."));
    }

    public Map<String, Object> list(long user) {
        return Map.of("success", true, "plants", plants(user).stream().map(StoredPlant::summary).toList());
    }

    public ResponseEntity<FileSystemResource> cover(long user, String plantId) {
        StoredPlant plant = requirePlant(user, plantId);
        return jdbc.query("""
                SELECT relative_path,mime_type FROM imagenes_planta WHERE batch_id=? AND user_id=?
                ORDER BY created_at,id LIMIT 1
                """, (rs, i) -> files.response(rs.getString(1), rs.getString(2)), plant.batchId, user)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Esta planta no tiene foto de identificación."));
    }

    JsonNode decode(String value) {
        try {
            return json.readTree(value);
        } catch (IOException error) {
            throw new IllegalStateException("El JSON almacenado no es válido.", error);
        }
    }

    private StoredPlant mapPlant(ResultSet rs) throws SQLException {
        return new StoredPlant(rs.getString("id"), rs.getString("batch_id"), rs.getString("scientific_name"),
                rs.getString("common_name"), rs.getString("family"), rs.getDouble("confidence"),
                date(rs, "created_at"), decode(rs.getString("result_json")));
    }

    static String date(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class).atOffset(ZoneOffset.UTC).toString();
    }

    static String newId() { return UUID.randomUUID().toString().replace("-", ""); }

    static String databaseId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) return id.toString().replace("-", "");
        } catch (IllegalArgumentException ignored) {
            // Los identificadores públicos tienen el formato UUID de Django.
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró el recurso en tu cuenta.");
    }

    static String publicId(String value) {
        return value.replaceFirst("^(.{8})(.{4})(.{4})(.{4})(.{12})$", "$1-$2-$3-$4-$5");
    }

    private ObjectNode state(String status) {
        return json.createObjectNode().put("success", true).put("status", status);
    }

    private ObjectNode insufficient(String plantName) {
        return state("insufficient").put("message", PlantCards.INSUFFICIENT).put("plant_name", plantName);
    }

    private void saveRun(long user, String batch, String organ, String status, JsonNode raw) {
        jdbc.update("""
                INSERT INTO procesos_identificacion (id,user_id,batch_id,organ,status,raw_json,created_at)
                VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6))
                """, newId(), user, batch, organ.isBlank() ? "auto" : organ.strip(), status, raw.toString());
    }

    private void savePlant(long user, String batch, JsonNode plant) {
        jdbc.update("""
                INSERT INTO identificaciones_planta
                (id,user_id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at)
                VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                """, newId(), user, batch, plant.path("scientific_name").asText(), plant.path("common_name").asText(),
                plant.path("family").asText(), plant.path("confidence").asDouble(), plant.toString());
    }

    public record StoredPlant(String id, String batchId, String scientific, String common, String family,
                              double confidence, String createdAt, JsonNode card) {
        public String publicId() { return UserPlants.publicId(id); }
        public String sessionId() { return "plant_" + publicId(); }
        Map<String, Object> summary() {
            return Map.of("id", publicId(), "scientific_name", scientific, "common_name", common, "family", family,
                    "confidence", confidence, "created_at", createdAt,
                    "image_url", "agronomia-api/api/plants/" + publicId() + "/cover");
        }
    }
}
