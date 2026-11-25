package com.agronomia.backend.user;

import com.agronomia.backend.images.PlantImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class PlantIdentificationApplicationService {
    private final PlantImageRepository images;
    private final UserPlantService plants;
    private final IdentificationPipelineService pipeline;
    private final Path uploadDir;

    public PlantIdentificationApplicationService(
            PlantImageRepository images,
            UserPlantService plants,
            IdentificationPipelineService pipeline,
            @Value("${plant.storage.directory:${PLANT_UPLOAD_DIR:C:/xampp/plant-images}}") String uploadDir
    ) {
        this.images = images;
        this.plants = plants;
        this.pipeline = pipeline;
        this.uploadDir = Path.of(uploadDir);
    }

    public Map<String, Object> identify(long userId, String batchId, String organ) {
        List<String> paths = images.findRelativePathsByBatchIdAndUserId(batchId, userId);
        if (paths.isEmpty()) {
            throw new UserApiException(HttpStatus.NOT_FOUND,
                    "No se encontraron imágenes para ese batch_id.");
        }

        List<byte[]> blobs = new ArrayList<>();
        for (String path : paths) {
            try {
                blobs.add(Files.readAllBytes(uploadDir.resolve(path)));
            } catch (Exception e) {
                throw new UserApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer una imagen almacenada.");
            }
        }

        Map<String, Object> result = pipeline.identify(blobs, organ);
        plants.saveRun(userId, batchId, organ, String.valueOf(result.get("status")), result.get("raw"));
        if ("failed".equals(result.get("status"))) {
            throw new UserApiException(HttpStatus.BAD_GATEWAY, String.valueOf(result.get("message")));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", result.get("status"));
        if ("identified".equals(result.get("status"))) {
            Map<String, Object> plant = asMap(result.get("plant"));
            plants.saveIdentification(userId, batchId, plant);
            response.put("plant", plant);
        } else if ("ambiguous".equals(result.get("status"))) {
            response.put("candidates", result.get("candidates"));
        } else {
            response.put("message", result.get("message"));
            if ("insufficient".equals(result.get("status"))) {
                response.put("plant_name", result.get("plant_name"));
            }
        }
        if (result.containsKey("candidates") && !response.containsKey("candidates")) {
            response.put("candidates", result.get("candidates"));
        }
        return response;
    }

    public Map<String, Object> confirm(long userId, String batchId, String scientificName) {
        Map<String, Object> raw = plants.loadLatestRunRaw(userId, batchId);
        List<Map<String, Object>> candidates = plants.loadLatestRunCandidates(userId, batchId);
        if (candidates.isEmpty()) {
            throw new UserApiException(HttpStatus.NOT_FOUND,
                    "No se encontr? una identificaci?n previa para ese lote.");
        }

        Map<String, Object> chosenResult = null;
        Object results = raw.get("results");
        if (results instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> result)) continue;
                Map<String, Object> species = asMap(result.get("species"));
                String name = firstNonBlank(
                        str(species.get("scientificNameWithoutAuthor")),
                        str(species.get("scientificName")));
                if (scientificName.equals(name)) {
                    chosenResult = asMap(result);
                    break;
                }
            }
        }

        Map<String, Object> chosenCandidate = candidates.stream()
                .filter(candidate -> scientificName.equals(candidate.get("scientific_name")))
                .findFirst()
                .orElse(null);
        if (chosenResult == null && chosenCandidate == null) {
            throw new UserApiException(HttpStatus.BAD_REQUEST,
                    "La especie elegida no coincide con las candidatas.");
        }
        if (chosenResult == null) {
            chosenResult = new LinkedHashMap<>();
            Map<String, Object> species = new LinkedHashMap<>();
            species.put("scientificNameWithoutAuthor", chosenCandidate.get("scientific_name"));
            species.put("scientificName", chosenCandidate.get("scientific_name"));
            species.put("commonNames", chosenCandidate.getOrDefault("common_names", List.of()));
            species.put("family", Map.of("scientificNameWithoutAuthor",
                    chosenCandidate.getOrDefault("family", "")));
            chosenResult.put("species", species);
            chosenResult.put("score", chosenCandidate.getOrDefault("confidence", 0));
        }

        Map<String, Object> plant = pipeline.generatePlantInfo(chosenResult, candidates);
        plants.saveIdentification(userId, batchId, plant);
        return Map.of("success", true, "status", "identified", "plant", plant);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
