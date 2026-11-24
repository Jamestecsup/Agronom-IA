package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OpenAiClient implements AiClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private final HttpClient client;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String key;
    private final String model;
    private final String visionModel;
    private final String auxiliaryModel;
    private final int maxRequests;
    private final boolean debugResponses;
    private final AtomicInteger requests = new AtomicInteger();

    @Autowired
    public OpenAiClient(ObjectMapper mapper, @Value("${ai.base-url}") String baseUrl,
                        @Value("${ai.api-key:}") String key, @Value("${ai.model}") String model,
                        @Value("${AI_VISION_MODEL:}") String visionModel,
                        @Value("${AI_AUX_MODEL:}") String auxiliaryModel,
                        @Value("${AI_MAX_REQUESTS:0}") int maxRequests,
                        @Value("${AI_DEBUG_RESPONSES:false}") boolean debugResponses) {
        this(mapper, baseUrl, key, model, visionModel, auxiliaryModel, maxRequests, TIMEOUT, debugResponses);
    }

    public OpenAiClient(ObjectMapper mapper, String baseUrl, String key, String model,
                        String visionModel, int maxRequests) {
        this(mapper, baseUrl, key, model, visionModel, "", maxRequests, TIMEOUT, false);
    }

    OpenAiClient(ObjectMapper mapper, String baseUrl, String key, String model,
                 String visionModel, int maxRequests, Duration timeout) {
        this(mapper, baseUrl, key, model, visionModel, "", maxRequests, timeout, false);
    }

    private OpenAiClient(ObjectMapper mapper, String baseUrl, String key, String model,
                         String visionModel, String auxiliaryModel, int maxRequests, Duration timeout,
                         boolean debugResponses) {
        this.mapper = mapper;
        this.endpoint = URI.create(baseUrl.replaceFirst("/+$", "") + "/chat/completions");
        this.key = key;
        this.model = model;
        this.visionModel = visionModel.isBlank() ? model : visionModel;
        this.auxiliaryModel = auxiliaryModel.isBlank() ? model : auxiliaryModel;
        this.maxRequests = maxRequests;
        this.debugResponses = debugResponses;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String reply(String step, List<Map<String, Object>> messages, boolean vision) {
        String result = call(step, messages, vision, vision ? visionModel : model, false, Deadline.after(timeout), true);
        if (result.isBlank()) throw new Failure("La IA devolvió una respuesta vacía.", false);
        return result.strip();
    }

    @Override
    public JsonNode json(String step, List<Map<String, Object>> messages, boolean vision) {
        return jsonWithin(step, messages, vision, Deadline.after(timeout), true).value();
    }

    @Override
    public JsonResponse jsonWithin(String step, List<Map<String, Object>> messages, boolean vision,
                                   Deadline deadline, boolean retryAllowed) {
        return jsonWithin(step, messages, vision, vision ? visionModel : model, deadline, retryAllowed);
    }

    @Override
    public JsonResponse auxiliaryJsonWithin(String step, List<Map<String, Object>> messages,
                                            Deadline deadline, boolean retryAllowed) {
        return jsonWithin(step, messages, false, auxiliaryModel, deadline, retryAllowed);
    }

    private JsonResponse jsonWithin(String step, List<Map<String, Object>> messages, boolean vision,
                                    String selectedModel, Deadline deadline, boolean retryAllowed) {
        for (int retry = 0; retry < (retryAllowed ? 2 : 1); retry++) {
            String content = call(step, messages, vision, selectedModel, true, deadline, retryAllowed);
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end >= start) {
                try {
                    JsonNode payload = mapper.readTree(content.substring(start, end + 1));
                    if (payload != null && payload.isObject()) return new JsonResponse(payload, content.length());
                } catch (IOException ignored) {
                    // El siguiente intento vuelve a pedir un objeto JSON.
                }
            }
            LOG.warn("IA paso={} respuesta JSON inválida o vacía intento={} caracteres={}", step, retry + 1, content.length());
        }
        throw new Failure("La IA devolvió una respuesta JSON inválida o vacía.", false);
    }

    private String call(String step, List<Map<String, Object>> messages, boolean vision, String selectedModel, boolean asJson,
                        Deadline deadline, boolean retryAllowed) {
        if (key.isBlank()) throw new Failure("La IA no está configurada.", false);
        var payload = mapper.createObjectNode()
                .put("model", selectedModel)
                .put("temperature", asJson ? 0.1 : 0.2);
        payload.set("messages", mapper.valueToTree(messages));
        if (asJson) payload.putObject("response_format").put("type", "json_object");
        for (int retry = 0; retry <= 2; retry++) {
            deadline.requireTime();
            if (maxRequests > 0 && requests.incrementAndGet() > maxRequests) {
                throw new Failure("Se alcanzó el presupuesto de llamadas a la IA.", false);
            }
            long started = System.nanoTime();
            long completed = 0L;
            int status = 0;
            try {
                Duration remaining = Duration.ofNanos(Math.max(1, deadline.cap(timeout).remainingNanos()));
                var request = HttpRequest.newBuilder(endpoint).timeout(remaining)
                        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                var response = receive(request, remaining);
                completed = System.nanoTime();
                status = response.statusCode();
                JsonNode body = objectOrMissing(response.body());
                if (status == 429 && retryAllowed && retry < 2 && waitForQuota(body, retry, deadline)) {
                    continue;
                }
                if (status < 200 || status >= 300 || body.hasNonNull("error")) {
                    if (vision && noImageSupport(body)) {
                        throw new Failure("El modelo de IA configurado no admite imágenes. Configura AI_VISION_MODEL.", true);
                    }
                    throw new Failure(status == 429 ? "La IA alcanzó su límite de uso." : "No se pudo consultar la IA.", false);
                }
                String content = body.path("choices").path(0).path("message").path("content").asText("");
                if (debugResponses) {
                    String preview = content.replace(key, "[redactado]").replaceAll("[\\p{Cntrl}]", " ");
                    LOG.info("IA paso={} respuesta inicial={}", step, preview.substring(0, Math.min(300, preview.length())));
                }
                return content;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new Failure("La consulta a la IA se interrumpió.", false);
            } catch (ExecutionException | TimeoutException error) {
                throw new Failure("La IA no respondió a tiempo o falló la conexión.", false);
            } finally {
                LOG.info("IA paso={} intento={} HTTP={} tiempo_ms={}", step, retry + 1, status,
                        ((completed == 0L ? System.nanoTime() : completed) - started) / 1_000_000);
            }
        }
        throw new Failure("No se pudo consultar la IA.", false);
    }

    private HttpResponse<String> receive(HttpRequest request, Duration remaining)
            throws InterruptedException, ExecutionException, TimeoutException {
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            // HttpRequest.timeout por sí solo no cubre un cuerpo que se demora tras la cabecera.
            return response.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException error) {
            response.cancel(true);
            throw error;
        }
    }

    private JsonNode objectOrMissing(String text) {
        try {
            JsonNode result = mapper.readTree(text);
            return result == null ? mapper.missingNode() : result;
        } catch (IOException error) {
            return mapper.missingNode();
        }
    }

    private static boolean waitForQuota(JsonNode body, int retry, Deadline deadline) throws InterruptedException {
        JsonNode detail = body.findValue("retryDelay");
        long millis = 8000L * (retry + 1);
        if (detail != null && detail.asText().matches("\\d+(\\.\\d+)?s")) {
            String seconds = detail.asText().replace("s", "");
            millis = (long) Math.min(90_000, Double.parseDouble(seconds) * 1000);
        }
        if (TimeUnit.MILLISECONDS.toNanos(millis) >= deadline.remainingNanos()) return false;
        Thread.sleep(millis);
        return true;
    }

    private static boolean noImageSupport(JsonNode body) {
        String detail = body.path("error").toString().toLowerCase(Locale.ROOT);
        return (detail.contains("image") || detail.contains("vision") || detail.contains("multimodal"))
                && (detail.contains("not support") || detail.contains("no endpoints") || detail.contains("unsupported")
                    || detail.contains("text-only") || detail.contains("image input"));
    }
}
