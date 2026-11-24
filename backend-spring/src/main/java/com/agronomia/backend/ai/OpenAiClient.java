package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OpenAiClient implements AiClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String key;
    private final String model;
    private final String visionModel;
    private final int maxRequests;
    private final AtomicInteger requests = new AtomicInteger();

    public OpenAiClient(ObjectMapper mapper, @Value("${ai.base-url}") String baseUrl,
                        @Value("${ai.api-key:}") String key, @Value("${ai.model}") String model,
                        @Value("${AI_VISION_MODEL:}") String visionModel,
                        @Value("${AI_MAX_REQUESTS:0}") int maxRequests) {
        this.mapper = mapper;
        this.endpoint = URI.create(baseUrl.replaceFirst("/+$", "") + "/chat/completions");
        this.key = key;
        this.model = model;
        this.visionModel = visionModel.isBlank() ? model : visionModel;
        this.maxRequests = maxRequests;
    }

    @Override
    public String reply(String step, List<Map<String, Object>> messages, boolean vision) {
        String result = call(step, messages, vision, false);
        if (result.isBlank()) throw new Failure("La IA devolvió una respuesta vacía.", false);
        return result.strip();
    }

    @Override
    public JsonNode json(String step, List<Map<String, Object>> messages, boolean vision) {
        for (int retry = 0; retry < 2; retry++) {
            String content = call(step, messages, vision, true);
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end >= start) {
                try {
                    JsonNode payload = mapper.readTree(content.substring(start, end + 1));
                    if (payload != null && payload.isObject()) return payload;
                } catch (IOException ignored) {
                    // El siguiente intento vuelve a pedir un objeto JSON.
                }
            }
            LOG.warn("IA paso={} respuesta JSON inválida o vacía intento={}", step, retry + 1);
        }
        throw new Failure("La IA devolvió una respuesta JSON inválida o vacía.", false);
    }

    private String call(String step, List<Map<String, Object>> messages, boolean vision, boolean asJson) {
        if (key.isBlank()) throw new Failure("La IA no está configurada.", false);
        var payload = mapper.createObjectNode()
                .put("model", vision ? visionModel : model)
                .put("temperature", asJson ? 0.1 : 0.2);
        payload.set("messages", mapper.valueToTree(messages));
        if (asJson) payload.putObject("response_format").put("type", "json_object");
        for (int retry = 0; retry <= 2; retry++) {
            long started = System.nanoTime();
            int status = 0;
            try {
                if (maxRequests > 0 && requests.incrementAndGet() > maxRequests) {
                    throw new Failure("Se alcanzó el presupuesto de llamadas a la IA.", false);
                }
                var request = HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                status = response.statusCode();
                JsonNode body = objectOrMissing(response.body());
                if (status == 429 && retry < 2) {
                    waitForQuota(body, retry);
                    continue;
                }
                if (status < 200 || status >= 300 || body.hasNonNull("error")) {
                    if (vision && noImageSupport(body)) {
                        throw new Failure("El modelo de IA configurado no admite imágenes. Configura AI_VISION_MODEL.", true);
                    }
                    throw new Failure(status == 429 ? "La IA alcanzó su límite de uso." : "No se pudo consultar la IA.", false);
                }
                return body.path("choices").path(0).path("message").path("content").asText("");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new Failure("La consulta a la IA se interrumpió.", false);
            } catch (IOException error) {
                throw new Failure("La IA no respondió a tiempo o falló la conexión.", false);
            } finally {
                LOG.info("IA paso={} intento={} HTTP={} tiempo_ms={}", step, retry + 1, status,
                        (System.nanoTime() - started) / 1_000_000);
            }
        }
        throw new Failure("No se pudo consultar la IA.", false);
    }

    private JsonNode objectOrMissing(String text) {
        try {
            JsonNode result = mapper.readTree(text);
            return result == null ? mapper.missingNode() : result;
        } catch (IOException error) {
            return mapper.missingNode();
        }
    }

    private static void waitForQuota(JsonNode body, int retry) throws InterruptedException {
        JsonNode detail = body.findValue("retryDelay");
        long millis = 8000L * (retry + 1);
        if (detail != null && detail.asText().matches("\\d+(\\.\\d+)?s")) {
            String seconds = detail.asText().replace("s", "");
            millis = (long) Math.min(90_000, Double.parseDouble(seconds) * 1000);
        }
        Thread.sleep(millis);
    }

    private static boolean noImageSupport(JsonNode body) {
        String detail = body.path("error").toString().toLowerCase(Locale.ROOT);
        return (detail.contains("image") || detail.contains("vision") || detail.contains("multimodal"))
                && (detail.contains("not support") || detail.contains("no endpoints") || detail.contains("unsupported")
                    || detail.contains("text-only") || detail.contains("image input"));
    }
}
