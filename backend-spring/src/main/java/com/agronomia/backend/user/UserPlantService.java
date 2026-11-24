package com.agronomia.backend.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserPlantService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    public UserPlantService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Map<String, Object>> listPlants(long userId) {
        return jdbc.query("SELECT id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at FROM plant_identifications WHERE user_id=? ORDER BY created_at DESC",
                (rs, i) -> {
                    Map<String, Object> plant = readJson(rs.getString("result_json"));
                    if (plant.isEmpty()) {
                        plant = new LinkedHashMap<>();
                        plant.put("scientific_name", rs.getString("scientific_name"));
                        plant.put("common_name", rs.getString("common_name"));
                        plant.put("family", rs.getString("family"));
                        plant.put("confidence", rs.getDouble("confidence"));
                    }
                    String id = rs.getString("id");
                    plant.put("id", id);
                    plant.put("batch_id", rs.getString("batch_id"));
                    plant.put("created_at", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    plant.put("image_url", "agronomia-api/api/plants/" + id + "/cover");
                    return plant;
                }, userId);
    }

    public Map<String, Object> findOwnedPlant(long userId, String plantId) {
        try {
            return jdbc.queryForObject("SELECT id,batch_id,scientific_name,common_name,result_json,created_at FROM plant_identifications WHERE id=? AND user_id=?",
                    (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", rs.getString("id"));
                        m.put("batch_id", rs.getString("batch_id"));
                        m.put("scientific_name", rs.getString("scientific_name"));
                        m.put("common_name", rs.getString("common_name"));
                        m.put("result_json", readJson(rs.getString("result_json")));
                        m.put("created_at", rs.getTimestamp("created_at").toLocalDateTime().toString());
                        return m;
                    }, plantId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new UserApiException(HttpStatus.NOT_FOUND, "No se encontró la planta solicitada.");
        }
    }

    public Optional<String> findCoverPath(long userId, String plantId) {
        Map<String, Object> plant = findOwnedPlant(userId, plantId);
        String batchId = (String) plant.get("batch_id");
        List<String> paths = jdbc.query("SELECT relative_path FROM plant_images WHERE batch_id=? AND user_id=? ORDER BY created_at ASC LIMIT 1",
                (rs, i) -> rs.getString("relative_path"), batchId, userId);
        return paths.stream().findFirst();
    }

    public void saveIdentification(long userId, String batchId, Map<String, Object> plant) {
        String id = UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO plant_identifications (id,user_id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id, userId, batchId,
                str(plant.get("scientific_name")), str(plant.get("common_name")), str(plant.get("family")),
                toDouble(plant.get("confidence")), writeJson(plant), Timestamp.valueOf(LocalDateTime.now()));
        plant.put("id", id);
    }

    public void saveRun(long userId, String batchId, String organ, String status, Object raw) {
        jdbc.update("INSERT INTO identification_runs (id,user_id,batch_id,organ,status,raw_json,created_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString().replace("-",""), userId, batchId, organ, status, writeJson(raw), Timestamp.valueOf(LocalDateTime.now()));
    }

    public Map<String, Object> loadLatestRunRaw(long userId, String batchId) {
        List<String> raws = jdbc.query("SELECT raw_json FROM identification_runs WHERE user_id=? AND batch_id=? ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> rs.getString("raw_json"), userId, batchId);
        if (raws.isEmpty()) return Map.of();
        return readJson(raws.get(0));
    }

    public List<Map<String, Object>> loadLatestRunCandidates(long userId, String batchId) {
        Map<String, Object> raw = loadLatestRunRaw(userId, batchId);
        Object results = raw.get("results");
        if (!(results instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> species = asMap(m.get("species"));
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("scientific_name", firstNonBlank(str(species.get("scientificNameWithoutAuthor")), str(species.get("scientificName"))));
            c.put("common_names", species.getOrDefault("commonNames", List.of()));
            c.put("family", str(asMap(species.get("family")).get("scientificNameWithoutAuthor")));
            c.put("confidence", toDouble(m.get("score")));
            out.add(c);
        }
        out.sort((a,b) -> Double.compare(toDouble(b.get("confidence")), toDouble(a.get("confidence"))));
        return out;
    }

    public String writeJson(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception e) { return "{}"; }
    }
    public Map<String, Object> readJson(String raw) {
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try { return json.readValue(raw, MAP); } catch (Exception e) { return new LinkedHashMap<>(); }
    }
    public List<Map<String, Object>> readJsonList(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        try { return json.readValue(raw, LIST); } catch (Exception e) { return new ArrayList<>(); }
    }
    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap(Object o) { return o instanceof Map<?,?> m ? (Map<String,Object>) m : new LinkedHashMap<>(); }
    public String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    public double toDouble(Object o) { if (o instanceof Number n) return n.doubleValue(); try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }
    public String firstNonBlank(String... vals) { for (String v : vals) if (v != null && !v.isBlank()) return v; return ""; }
}
