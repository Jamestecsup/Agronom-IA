package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenWebUiChatProviderTest {
    private HttpServer server;
    private AiProviderProperties properties;
    private OpenWebUiChatProvider provider;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private boolean rejectChat;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"data\":[{\"id\":\"Qwen/Qwen3.6-35B-A3B-FP8\",\"info\":{\"meta\":{\"capabilities\":{\"vision\":true}}}}]}", "application/json");
        });
        server.createContext("/api/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (rejectChat) {
                respond(exchange, 429, "{\"error\":{\"message\":\"limit\"}}", "application/json");
            } else {
                String events = "data: {\"choices\":[{\"delta\":{\"content\":\"Hola \"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"planta\"}}]}\n\n"
                        + "data: [DONE]\n\n";
                respond(exchange, 200, events, "text/event-stream");
            }
        });
        server.start();
        properties = new AiProviderProperties();
        properties.getOpenWebUi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getOpenWebUi().setApiKey("test-secret");
        properties.getOpenWebUi().setModel("Qwen/Qwen3.6-35B-A3B-FP8");
        properties.getOpenWebUi().setConnectTimeout(Duration.ofSeconds(2));
        properties.getOpenWebUi().setReadTimeout(Duration.ofSeconds(10));
        provider = new OpenWebUiChatProvider(properties);
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void verifiesModelCatalogAndStreamsDeltasWithBackendCredential() {
        assertThat(provider.availability().available()).isTrue();
        assertThat(authorization).hasValue("Bearer test-secret");
        var output = new StringBuilder();

        provider.stream(List.of(new ChatContracts.ChatMessage("user", "¿Cómo cuido una planta?")),
                "Instrucciones botánicas", output::append, new ChatCancellation());

        assertThat(output).hasToString("Hola planta");
        assertThat(authorization).hasValue("Bearer test-secret");
        var request = parseBody();
        assertThat(request.path("model").asText()).isEqualTo("Qwen/Qwen3.6-35B-A3B-FP8");
        assertThat(request.path("stream").asBoolean()).isTrue();
        assertThat(request.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(request.path("messages").get(0).path("content").asText()).isEqualTo("Instrucciones botánicas");
        assertThat(request.path("messages").get(1).path("content").asText()).contains("planta");
        assertThat(requestBody.get()).doesNotContain("test-secret");
    }

    @Test
    void sendsImagePartsUsingOpenAiCompatibleContentAndReportsVisionCapability() throws Exception {
        assertThat(provider.availability().supportsImages()).isTrue();
        byte[] png = pngFixture();
        var image = new ChatContracts.ChatImage("leaf.png", "image/png", png);
        var output = new StringBuilder();

        provider.stream(List.of(new ChatContracts.ChatMessage("user", "Describe this plant", List.of(image))),
                "Instrucciones botánicas", output::append, new ChatCancellation());

        var userContent = parseBody().path("messages").get(1).path("content");
        assertThat(userContent.isArray()).isTrue();
        assertThat(userContent.get(0).path("text").asText()).isEqualTo("Describe this plant");
        assertThat(userContent.get(1).path("type").asText()).isEqualTo("image_url");
        assertThat(userContent.get(1).path("image_url").path("url").asText()).startsWith("data:image/png;base64,");
        assertThat(userContent.get(1).path("image_url").path("url").asText()).contains(java.util.Base64.getEncoder().encodeToString(png));
    }

    @Test
    void convertsProviderRateLimitToAHandledError() {
        rejectChat = true;
        assertThatThrownBy(() -> provider.stream(List.of(new ChatContracts.ChatMessage("user", "hola")), "system",
                ignored -> { }, new ChatCancellation()))
                .isInstanceOf(ChatProviderException.class)
                .extracting(error -> ((ChatProviderException) error).status())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private com.fasterxml.jackson.databind.JsonNode parseBody() {
        try { return new ObjectMapper().readTree(requestBody.get()); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body, String type) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private byte[] pngFixture() throws Exception {
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(16, 16, 0x228833);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
