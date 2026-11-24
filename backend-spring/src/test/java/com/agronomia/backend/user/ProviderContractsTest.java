package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.agronomia.backend.ai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderContractsTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private String base;
    @TempDir Path temp;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void usesProviderDelayAndRetriesInvalidJsonOnce() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/api/v1/chat/completions", exchange -> {
            switch (attempts.incrementAndGet()) {
                case 1 -> send(exchange, 429, "{\"error\":{\"retryDelay\":\"0s\"}}");
                case 2 -> completion(exchange, "salida inválida");
                default -> completion(exchange, "Resultado: {\"prompt\":\"correcto\"}");
            }
        });
        var ai = new OpenAiClient(json, base + "/api/v1", "fixture", "modelo", "", 3);
        JsonNode result = ai.json("refine", List.of(AiClient.message("user", "prueba")), false);
        assertThat(result.path("prompt").asText()).isEqualTo("correcto");
        assertThat(attempts).hasValue(3);
        assertThatThrownBy(() -> ai.reply("chat", List.of(), false)).isInstanceOf(AiClient.Failure.class);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void unsupportedVisionSurfacesAnExplicitErrorEvenWhenProviderReturns404() {
        server.createContext("/chat/completions", exchange -> send(exchange, 404,
                "{\"error\":{\"message\":\"No endpoints support image input\"}}"));
        var ai = new OpenAiClient(json, base, "fixture", "texto", "vision", 1);
        assertThatThrownBy(() -> ai.reply("chat", List.of(), true))
                .isInstanceOfSatisfying(AiClient.Failure.class, error -> assertThat(error.visionUnsupported()).isTrue());
    }

    @Test
    void plantNetReceivesMultipartAndReturnsTheRankedResults() throws IOException {
        server.createContext("/v2/identify/all", exchange -> {
            String content = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!content.contains("name=\"images\"") || !content.contains("name=\"organs\"")) {
                send(exchange, 400, "{}");
                return;
            }
            send(exchange, 200, "{\"results\":[{\"score\":0.4,\"species\":{\"scientificNameWithoutAuthor\":\"Helianthus annuus\"}}]}");
        });
        Path file = Files.write(temp.resolve("fixture.png"), new byte[]{1, 2, 3});
        var plant = new PlantNetClient(base, "fixture", "all", "es");
        assertThat(plant.identify(List.of(new PlantNetClient.Photo(file, "image/png")), "auto")
                .path("results").path(0).path("score").asDouble()).isEqualTo(0.4);
    }

    private void completion(HttpExchange exchange, String content) throws IOException {
        var response = Map.of("choices", List.of(Map.of("message", Map.of("content", content))));
        send(exchange, 200, json.writeValueAsString(response));
    }

    private static void send(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
