package com.agronomia.backend.user;

import com.agronomia.backend.images.PlantImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "plant.storage.directory=target/integration-image-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPlantsApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserTokenService tokens;
    @MockBean UserPlantService plants;
    @MockBean PlantImageRepository plantImages;
    @MockBean IdentificationPipelineService pipeline;

    @Test
    void plantsEndpointReturnsContractWithSuccessAndPlantsArray() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plants.listPlants(1L)).thenReturn(List.of(Map.of(
                "id", "p1",
                "scientific_name", "Helianthus annuus",
                "image_url", "agronomia-api/api/plants/p1/cover"
        )));

        mockMvc.perform(get("/agronomia-api/api/plants").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.plants").isArray())
                .andExpect(jsonPath("$.plants[0].scientific_name").value("Helianthus annuus"))
                .andExpect(jsonPath("$.plants[0].image_url").value("agronomia-api/api/plants/p1/cover"));
    }

    @Test
    void plantsEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/agronomia-api/api/plants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void coverEndpointReturnsTheOwnedPlantImage() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plants.findCoverPath(1L, "p1")).thenReturn(Optional.of("contract/cover.png"));
        Path image = Path.of("target/integration-image-storage/contract/cover.png");
        Files.createDirectories(image.getParent());
        byte[] bytes = new byte[]{1, 2, 3, 4};
        Files.write(image, bytes);

        try {
            mockMvc.perform(get("/agronomia-api/api/plants/p1/cover").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                    .andExpect(content().bytes(bytes));
        } finally {
            Files.deleteIfExists(image);
        }
    }

    @Test
    void identifyEndpointPreservesAmbiguousContractAndUsesOwnedBatchImages() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plantImages.findRelativePathsByBatchIdAndUserId("batch-1", 1L))
                .thenReturn(List.of("contract/identify.jpg"));
        Path image = Path.of("target/integration-image-storage/contract/identify.jpg");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{5, 6, 7});
        Map<String, Object> candidate = Map.of("scientific_name", "Rosa canina", "confidence", 0.4);
        when(pipeline.identify(anyList(), org.mockito.ArgumentMatchers.eq("leaf")))
                .thenReturn(Map.of("status", "ambiguous", "candidates", List.of(candidate), "raw", Map.of()));

        try {
            mockMvc.perform(post("/agronomia-api/api/identify")
                            .param("batch_id", "batch-1")
                            .param("organ", "leaf")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("ambiguous"))
                    .andExpect(jsonPath("$.candidates[0].scientific_name").value("Rosa canina"));
        } finally {
            Files.deleteIfExists(image);
        }
    }

    @Test
    void identifyEndpointPreservesIdentifiedContractAndSavesPlant() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plantImages.findRelativePathsByBatchIdAndUserId("batch-1", 1L))
                .thenReturn(writeImage("contract/identified.jpg"));
        Map<String, Object> plant = new LinkedHashMap<>(Map.of(
                "scientific_name", "Rosa canina", "common_name", "Escaramujo", "confidence", 0.8));
        when(pipeline.identify(anyList(), eq("auto")))
                .thenReturn(Map.of("status", "identified", "plant", plant, "raw", Map.of("results", List.of())));
        doAnswer(invocation -> {
            ((Map<String, Object>) invocation.getArgument(2)).put("id", "plant-1");
            return null;
        }).when(plants).saveIdentification(eq(1L), eq("batch-1"), anyMap());

        try {
            mockMvc.perform(post("/agronomia-api/api/identify")
                            .param("batch_id", "batch-1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("identified"))
                    .andExpect(jsonPath("$.plant.id").value("plant-1"))
                    .andExpect(jsonPath("$.plant.scientific_name").value("Rosa canina"));
        } finally {
            deleteImage("contract/identified.jpg");
        }
        verify(plants).saveRun(eq(1L), eq("batch-1"), eq("auto"), eq("identified"), any());
        verify(plants).saveIdentification(eq(1L), eq("batch-1"), anyMap());
    }

    @Test
    void identifyEndpointPreservesInsufficientContract() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plantImages.findRelativePathsByBatchIdAndUserId("batch-1", 1L))
                .thenReturn(writeImage("contract/insufficient.jpg"));
        when(pipeline.identify(anyList(), eq("auto"))).thenReturn(Map.of(
                "status", "insufficient",
                "message", "No encontramos información suficiente.",
                "plant_name", "Rosa canina",
                "candidates", List.of(Map.of("scientific_name", "Rosa canina")),
                "raw", Map.of()));

        try {
            mockMvc.perform(post("/agronomia-api/api/identify")
                            .param("batch_id", "batch-1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("insufficient"))
                    .andExpect(jsonPath("$.message").value("No encontramos información suficiente."))
                    .andExpect(jsonPath("$.plant_name").value("Rosa canina"))
                    .andExpect(jsonPath("$.candidates[0].scientific_name").value("Rosa canina"));
        } finally {
            deleteImage("contract/insufficient.jpg");
        }
        verify(plants).saveRun(eq(1L), eq("batch-1"), eq("auto"), eq("insufficient"), any());
        verify(plants, never()).saveIdentification(anyLong(), anyString(), anyMap());
    }

    @Test
    void identifyEndpointReturnsNotFoundWhenBatchDoesNotBelongToUser() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plantImages.findRelativePathsByBatchIdAndUserId("batch-1", 2L))
                .thenReturn(List.of("another-users/batch-image.jpg"));
        when(plantImages.findRelativePathsByBatchIdAndUserId("batch-1", 1L)).thenReturn(List.of());

        mockMvc.perform(post("/agronomia-api/api/identify")
                        .param("batch_id", "batch-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("No se encontraron imágenes para ese batch_id."));

        verify(plantImages).findRelativePathsByBatchIdAndUserId("batch-1", 1L);
        verify(pipeline, never()).identify(anyList(), anyString());
    }

    @Test
    void identifyEndpointPreservesFailedStatusAsGatewayError() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plantImages.findRelativePathsByBatchIdAndUserId("batch-1", 1L))
                .thenReturn(writeImage("contract/failed.jpg"));
        when(pipeline.identify(anyList(), eq("auto"))).thenReturn(Map.of(
                "status", "failed", "message", "Pl@ntNet no disponible.", "raw", Map.of()));

        try {
            mockMvc.perform(post("/agronomia-api/api/identify")
                            .param("batch_id", "batch-1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").value("Pl@ntNet no disponible."));
        } finally {
            deleteImage("contract/failed.jpg");
        }
        verify(plants).saveRun(eq(1L), eq("batch-1"), eq("auto"), eq("failed"), any());
    }

    @Test
    void confirmEndpointPreservesIdentifiedContractForChosenCandidate() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plants.loadLatestRunRaw(1L, "batch-1")).thenReturn(Map.of());
        when(plants.loadLatestRunCandidates(1L, "batch-1")).thenReturn(List.of(Map.of(
                "scientific_name", "Rosa canina", "common_names", List.of("Escaramujo"),
                "family", "Rosaceae", "confidence", 0.7)));
        when(pipeline.generatePlantInfo(anyMap(), anyList())).thenReturn(Map.of(
                "scientific_name", "Rosa canina", "common_name", "Escaramujo", "confidence", 0.7));

        mockMvc.perform(post("/agronomia-api/api/confirm")
                        .param("batch_id", "batch-1")
                        .param("scientific_name", "Rosa canina")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("identified"))
                .andExpect(jsonPath("$.plant.scientific_name").value("Rosa canina"));
        verify(plants).loadLatestRunRaw(1L, "batch-1");
        verify(plants).loadLatestRunCandidates(1L, "batch-1");
        verify(plants).saveIdentification(eq(1L), eq("batch-1"), anyMap());
        verify(pipeline).generatePlantInfo(anyMap(), anyList());
    }

    @Test
    void confirmEndpointDoesNotReadAnotherUsersBatch() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(1L, "tester")));
        when(plants.loadLatestRunCandidates(2L, "batch-1")).thenReturn(List.of(Map.of(
                "scientific_name", "Rosa canina", "confidence", 0.8)));
        when(plants.loadLatestRunRaw(1L, "batch-1")).thenReturn(Map.of());
        when(plants.loadLatestRunCandidates(1L, "batch-1")).thenReturn(List.of());

        mockMvc.perform(post("/agronomia-api/api/confirm")
                        .param("batch_id", "batch-1")
                        .param("scientific_name", "Rosa canina")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(plants).loadLatestRunRaw(1L, "batch-1");
        verify(plants).loadLatestRunCandidates(1L, "batch-1");
        verify(pipeline, never()).generatePlantInfo(anyMap(), anyList());
    }

    private List<String> writeImage(String relativePath) throws Exception {
        Path image = Path.of("target/integration-image-storage").resolve(relativePath);
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{8, 9, 10});
        return List.of(relativePath);
    }

    private void deleteImage(String relativePath) throws Exception {
        Files.deleteIfExists(Path.of("target/integration-image-storage").resolve(relativePath));
    }
}
