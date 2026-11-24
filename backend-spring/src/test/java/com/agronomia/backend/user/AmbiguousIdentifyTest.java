package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AmbiguousIdentifyTest {
    @TempDir Path storage;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void ambiguousIdentifyReturnsEmptyHintsPromptlyWithoutAi() throws Exception {
        var json = new ObjectMapper();
        var ai = mock(AiClient.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.registerBean(ObjectMapper.class, () -> json);
            context.registerBean(AiClient.class, () -> ai);
            context.register(PlantCards.class);
            context.refresh();
            var cards = context.getBean(PlantCards.class);
            var jdbc = mock(JdbcTemplate.class);
            var photos = List.of(new PlantNetClient.Photo(storage.resolve("fixture.png"), "image/png"));
            when(jdbc.query(contains("SELECT relative_path"), any(RowMapper.class), eq("lote"), eq(7L)))
                    .thenReturn(photos);
            var plantNet = mock(PlantNetClient.class);
            when(plantNet.identify(eq(photos), eq("auto"), any(AiClient.Deadline.class))).thenReturn(json.readTree("""
                    {"results":[
                      {"score":0.32,"species":{"scientificNameWithoutAuthor":"Helianthus annuus"}},
                      {"score":0.44,"species":{"scientificNameWithoutAuthor":"Helianthus giganteus"}}
                    ]}
                    """));
            var tx = mock(PlatformTransactionManager.class);
            when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            var accounts = mock(UserAccounts.class);
            when(accounts.requireUser("Bearer fixture")).thenReturn(7L);
            var plants = new UserPlants(jdbc, json, cards, plantNet, new UserFiles(storage.toString()), tx);
            var mvc = standaloneSetup(new UserPlantsController(accounts, plants)).build();

            assertTimeout(Duration.ofSeconds(3), () -> mvc.perform(post("/agronomia-api/api/identify")
                            .header("Authorization", "Bearer fixture")
                            .param("batch_id", "lote").param("organ", "auto"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ambiguous"))
                    .andExpect(jsonPath("$.candidates.length()").value(2))
                    .andExpect(jsonPath("$.candidates[0].scientific_name").value("Helianthus giganteus"))
                    .andExpect(jsonPath("$.candidates[0].hint").value(""))
                    .andExpect(jsonPath("$.candidates[0].terms").isEmpty())
                    .andExpect(jsonPath("$.candidates[1].hint").value(""))
                    .andExpect(jsonPath("$.candidates[1].terms").isEmpty()));
            verifyNoInteractions(ai);
        }
    }
}
