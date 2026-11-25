package com.agronomia.backend.user;

import com.agronomia.backend.images.PlantImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "plant.storage.directory=target/integration-image-storage")
@ActiveProfiles("test")
class PlantIdentificationRepositoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserPlantService plants;
    @Autowired PlantIdentificationRepository plantRepository;
    @Autowired PlantImageRepository imageRepository;

    @BeforeEach
    void clearTestRows() {
        jdbc.update("DELETE FROM identification_runs");
        jdbc.update("DELETE FROM plant_identifications");
        jdbc.update("DELETE FROM plant_images");
    }

    @Test
    void persistsPlantAndPreservesListAndOwnershipShapes() {
        Map<String, Object> plant = new java.util.LinkedHashMap<>();
        plant.put("scientific_name", "Helianthus annuus");
        plant.put("common_name", "Girasol");
        plant.put("family", "Asteraceae");
        plant.put("confidence", 0.91);
        plant.put("sections", List.of(Map.of("key", "care", "body", "Sol")));

        plants.saveIdentification(7L, "batch-7", plant);

        assertThat(plant).containsKey("id");
        assertThat(plants.listPlants(7L)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("id", plant.get("id"));
            assertThat(row).containsEntry("batch_id", "batch-7");
            assertThat(row).containsEntry("scientific_name", "Helianthus annuus");
            assertThat(row).containsEntry("image_url", "agronomia-api/api/plants/" + plant.get("id") + "/cover");
            assertThat(row).containsKey("created_at");
        });

        Map<String, Object> owned = plants.findOwnedPlant(7L, String.valueOf(plant.get("id")));
        assertThat(owned).containsEntry("batch_id", "batch-7");
        assertThat(owned.get("result_json")).isInstanceOf(Map.class);
        assertThat(plantRepository.findByIdAndUserId(String.valueOf(plant.get("id")), 99L)).isEmpty();
    }

    @Test
    void readsOnlyOwnersBatchImagesAndFindsTheCover() {
        Map<String, Object> plant = new java.util.LinkedHashMap<>();
        plant.put("scientific_name", "Rosa canina");
        plant.put("confidence", 0.8);
        plants.saveIdentification(7L, "batch-cover", plant);
        jdbc.update("INSERT INTO plant_images (id,batch_id,relative_path,mime_type,file_size,width,height,user_id) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), "batch-cover", "first.jpg", "image/jpeg", 10L, 20, 20, 7L);
        jdbc.update("INSERT INTO plant_images (id,batch_id,relative_path,mime_type,file_size,width,height,user_id) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), "batch-cover", "other-user.jpg", "image/jpeg", 10L, 20, 20, 8L);

        assertThat(imageRepository.findRelativePathsByBatchIdAndUserId("batch-cover", 7L))
                .containsExactly("first.jpg");
        assertThat(imageRepository.findFirstRelativePathByBatchIdAndUserId("batch-cover", 7L))
                .contains("first.jpg");
        assertThat(imageRepository.findFirstRelativePathByBatchIdAndUserId("batch-cover", 8L))
                .contains("other-user.jpg");
        assertThat(plants.findCoverPath(7L, String.valueOf(plant.get("id"))))
                .contains("first.jpg");
    }

    @Test
    void persistsRawIdentificationRunAndExtractsSortedCandidates() {
        Map<String, Object> raw = Map.of("results", List.of(
                Map.of("score", 0.4, "species", Map.of("scientificNameWithoutAuthor", "Rosa alba")),
                Map.of("score", 0.8, "species", Map.of(
                        "scientificNameWithoutAuthor", "Rosa canina",
                        "commonNames", List.of("Escaramujo"),
                        "family", Map.of("scientificNameWithoutAuthor", "Rosaceae")))
        ));

        plants.saveRun(7L, "batch-run", "leaf", "ambiguous", raw);

        assertThat(plants.loadLatestRunRaw(7L, "batch-run")).containsKey("results");
        assertThat(plants.loadLatestRunCandidates(7L, "batch-run"))
                .extracting(row -> row.get("scientific_name"))
                .containsExactly("Rosa canina", "Rosa alba");
        assertThat(plants.loadLatestRunRaw(8L, "batch-run")).isEmpty();
    }
}
