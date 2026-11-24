package com.agronomia.backend.user;

import com.agronomia.backend.ai.OpenAiClient;
import com.agronomia.backend.images.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SlowConfirmTest {
    @TempDir Path storage;

    @Test
    @Timeout(175)
    @SuppressWarnings({"unchecked", "rawtypes"})
    void confirmRespondsBefore180SecondsWhenCategoriesBodyIsSlow() throws Exception {
        var json = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var categoryHeaders = new CountDownLatch(1);
        var completeSlowBody = new CountDownLatch(1);
        server.createContext("/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (request.contains("Genera una ficha botánica")) {
                exchange.sendResponseHeaders(200, 256);
                categoryHeaders.countDown();
                try {
                    completeSlowBody.await(160, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            } else {
                byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"prompt\\\":\\\"Guía\\\"}\"}}]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (var body = exchange.getResponseBody()) { body.write(response); }
            }
        });
        server.start();
        var client = new OpenAiClient(json, "http://127.0.0.1:" + server.getAddress().getPort(),
                "fixture", "doble-local", "", 0);
        var cards = new PlantCards(json, client, false, "");
        try {
            var jdbc = mock(JdbcTemplate.class);
            var raw = json.readTree("""
                    {"results":[{"species":{"scientificNameWithoutAuthor":"Helianthus annuus"},"score":0.44}]}
                    """);
            when(jdbc.query(contains("SELECT raw_json"), any(RowMapper.class), eq("lote"), eq(7L)))
                    .thenReturn(List.of(raw));
            var accounts = mock(UserAccounts.class);
            when(accounts.requireUser("Bearer fixture")).thenReturn(7L);
            var plants = new UserPlants(jdbc, json, cards, mock(PlantNetClient.class),
                    new UserFiles(storage.toString()), mock(PlatformTransactionManager.class));
            var mvc = standaloneSetup(new UserPlantsController(accounts, plants))
                    .setControllerAdvice(new ApiExceptionHandler()).build();

            long started = System.nanoTime();
            mvc.perform(post("/agronomia-api/api/confirm")
                    .header("Authorization", "Bearer fixture")
                    .param("batch_id", "lote").param("scientific_name", "Helianthus annuus"))
                    .andExpect(status().isBadGateway());
            assertThat(categoryHeaders.getCount()).isZero();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(180));
            assertThat(elapsed).isGreaterThan(Duration.ofSeconds(80));
        } finally {
            completeSlowBody.countDown();
            cards.stop();
            server.stop(0);
        }
    }
}
