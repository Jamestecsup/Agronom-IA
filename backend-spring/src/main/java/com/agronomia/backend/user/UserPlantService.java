package com.agronomia.backend.user;

import com.agronomia.backend.images.PlantImageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserPlantService {
    private final PlantIdentificationRepository plants;
    private final PlantImageRepository images;
    private final ObjectMapper json;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    public UserPlantService(PlantIdentificationRepository plants, PlantImageRepository images,
                            ObjectMapper json) {
        this.plants = plants;
        this.images = images;
        this.json = json;
    }

    public List<Map<String, Object>> listPlants(long userId) {
        return plants.findAllByUserId(userId).stream().map(row -> {
            Map<String, Object> plant = readJson(row.resultJson());
            if (plant.isEmpty()) {
                plant = new LinkedHashMap<>();
                plant.put("scientific_name", row.scientificName());
                plant.put("common_name", row.commonName());
                plant.put("family", row.family());
                plant.put("confidence", row.confidence());
            }
            plant.put("id", row.id());
            plant.put("batch_id", row.batchId());
            plant.put("created_at", row.createdAt().toString());
            plant.put("image_url", "agronomia-api/api/plants/" + row.id() + "/cover");
            return plant;
        }).toList();
    }

    public Map<String, Object> findOwnedPlant(long userId, String plantId) {
        PlantIdentificationRepository.PlantRow row = plants.findByIdAndUserId(plantId, userId)
                .orElseThrow(() -> new UserApiException(HttpStatus.NOT_FOUND,
                        "No se encontró la planta solicitada."));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.id());
        result.put("batch_id", row.batchId());
        result.put("scientific_name", row.scientificName());
        result.put("common_name", row.commonName());
        result.put("result_json", readJson(row.resultJson()));
        result.put("created_at", row.createdAt().toString());
        return result;
    }

    public Optional<String> findCoverPath(long userId, String plantId) {
        Map<String, Object> plant = findOwnedPlant(userId, plantId);
        String batchId = (String) plant.get("batch_id");
        return images.findFirstRelativePathByBatchIdAndUserId(batchId, userId);
    }

    public void saveIdentification(long userId, String batchId, Map<String, Object> plant) {
        String id = UUID.randomUUID().toString().replace("-","");
        plants.insertPlant(id, userId, batchId,
                str(plant.get("scientific_name")), str(plant.get("common_name")), str(plant.get("family")),
                toDouble(plant.get("confidence")), writeJson(plant), LocalDateTime.now());
        plant.put("id", id);
    }

    public void saveRun(long userId, String batchId, String organ, String status, Object raw) {
        plants.insertRun(UUID.randomUUID().toString().replace("-", ""), userId, batchId,
                organ, status, writeJson(raw), LocalDateTime.now());
    }

    public Map<String, Object> loadLatestRunRaw(long userId, String batchId) {
        return plants.findLatestRunRawJson(userId, batchId).map(this::readJson).orElseGet(Map::of);
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
