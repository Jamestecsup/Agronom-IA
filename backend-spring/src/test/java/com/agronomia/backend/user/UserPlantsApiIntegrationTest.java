package com.agronomia.backend.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "plant.storage.directory=target/integration-image-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPlantsApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserTokenService tokens;
    @MockBean UserPlantService plants;

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
}
