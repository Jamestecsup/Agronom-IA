package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Primary
@Component
public class AiProviderResolver implements AiClient {
    private static final String PIXEL = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lF0AAAAASUVORK5CYII=";
    private final OpenAiClient system;
    private final ProviderStore store;
    private final ProviderTransport transport;
    private final ObjectMapper json;
    private final int systemQuota;
    private final ConcurrentHashMap<Long, Limits> limits = new ConcurrentHashMap<>();

    AiProviderResolver(OpenAiClient system, ProviderStore store, ProviderTransport transport, ObjectMapper json,
                       @Value("${AI_SYSTEM_DAILY_QUOTA:100}") int systemQuota) {
        if (systemQuota < 1) throw new IllegalArgumentException("AI_SYSTEM_DAILY_QUOTA debe ser positivo.");
        this.system = system;
        this.store = store;
        this.transport = transport;
        this.json = json;
        this.systemQuota = systemQuota;
    }

    @Override public String reply(String step, List<Map<String, Object>> messages, boolean vision) {
        return system.reply(step, messages, vision);
    }

    @Override public JsonNode json(String step, List<Map<String, Object>> messages, boolean vision) {
        return system.json(step, messages, vision);
    }

    @Override public AiClient forUser(long owner) {
        var profile = store.active(owner);
        return profile == null ? new SystemClient(owner) : new CustomClient(profile);
    }

    ProviderStore.Profile probe(long owner, String id) {
        var profile = store.require(owner, id);
        var limiter = limits.computeIfAbsent(owner, unused -> new Limits());
        limiter.begin(profile.concurrency(), true);
        try {
            long started = System.nanoTime();
            var deadline = Deadline.after(Duration.ofSeconds(28));
            var plain = perform(profile, List.of(AiClient.message("user", "Responde OK.")), false, deadline.cap(Duration.ofSeconds(8)));
            if (content(plain).isBlank()) {
                return store.recordProbe(profile, false, false, false, profile.context(), latency(started));
            }
            var structured = perform(profile, List.of(AiClient.message("user", "Responde con el JSON {\"ok\":true}.")),
                    true, deadline.cap(Duration.ofSeconds(6)));
            boolean jsonMode = parseObject(content(structured)).path("ok").asBoolean(false);
            var image = Map.<String, Object>of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", "Responde OK si recibiste la imagen."),
                    Map.of("type", "image_url", "image_url", Map.of("url", PIXEL))));
            boolean vision = !content(perform(profile, List.of(image), false, deadline.cap(Duration.ofSeconds(7)))).isBlank();
            int context = profile.context();
            if (deadline.remainingNanos() > 2_000_000_000L) {
                var models = transport.call(profile, "/models", null, deadline.cap(Duration.ofSeconds(4)));
                for (var model : models.body().path("data")) {
                    if (!profile.model().equals(model.path("id").asText())) continue;
                    int reported = model.path("context_length").asInt(model.path("context_window").asInt(0));
                    if (reported >= 1024 && reported <= 262144) context = Math.min(context, reported);
                }
            }
            return store.recordProbe(profile, true, vision, jsonMode, context, latency(started));
        } finally {
            limiter.end();
        }
    }

    private static long latency(long started) { return (System.nanoTime() - started) / 1_000_000; }

    private ProviderTransport.Response perform(ProviderStore.Profile profile, List<Map<String, Object>> messages,
                                               boolean jsonMode, Deadline deadline) {
        var body = json.createObjectNode().put("model", profile.model()).put("max_tokens", profile.output());
        body.set("messages", json.valueToTree(messages));
        if (jsonMode) body.putObject("response_format").put("type", "json_object");
        return transport.call(profile, "/chat/completions", body, deadline);
    }

    private static String content(ProviderTransport.Response response) {
        return response.body().path("choices").path(0).path("message").path("content").asText("");
    }

    private JsonNode parseObject(String text) {
        int first = text.indexOf('{'), last = text.lastIndexOf('}');
        if (first >= 0 && last > first) {
            try { return json.readTree(text.substring(first, last + 1)); }
            catch (java.io.IOException ignored) {}
        }
        return json.missingNode();
    }

    private final class SystemClient implements AiClient {
        private final long owner;
        private SystemClient(long owner) { this.owner = owner; }

        @Override public String reply(String step, List<Map<String, Object>> messages, boolean vision) {
            return system.withCharge(() -> store.useSystem(owner, systemQuota), () -> system.reply(step, messages, vision));
        }
        @Override public JsonNode json(String step, List<Map<String, Object>> messages, boolean vision) {
            return system.withCharge(() -> store.useSystem(owner, systemQuota), () -> system.json(step, messages, vision));
        }
        @Override public JsonResponse jsonWithin(String step, List<Map<String, Object>> messages, boolean vision, Deadline deadline, boolean retry) {
            return system.withCharge(() -> store.useSystem(owner, systemQuota),
                    () -> system.jsonWithin(step, messages, vision, deadline, retry));
        }
        @Override public JsonResponse auxiliaryJsonWithin(String step, List<Map<String, Object>> messages, Deadline deadline, boolean retry) {
            return system.withCharge(() -> store.useSystem(owner, systemQuota),
                    () -> system.auxiliaryJsonWithin(step, messages, deadline, retry));
        }
    }

    private final class CustomClient implements AiClient {
        private final ProviderStore.Profile profile;
        private volatile double tokenAdjustment = 1;
        private CustomClient(ProviderStore.Profile profile) { this.profile = profile; }

        @Override public int concurrency() { return profile.concurrency(); }
        @Override public boolean supportsVision() { return profile.vision(); }
        @Override public int inputBudget() { return profile.context() - profile.output(); }
        @Override public int outputBudget() { return profile.output(); }
        @Override public int estimateCharacters(int characters) { return (int) Math.ceil(characters / 3.0 * 1.2 * tokenAdjustment); }

        @Override public String reply(String step, List<Map<String, Object>> messages, boolean vision) {
            return request(messages, vision, false, Deadline.after(Duration.ofSeconds(45)));
        }

        @Override public JsonNode json(String step, List<Map<String, Object>> messages, boolean vision) {
            return jsonWithin(step, messages, vision, Deadline.after(Duration.ofSeconds(45)), false).value();
        }

        @Override public JsonResponse jsonWithin(String step, List<Map<String, Object>> messages, boolean vision,
                                                Deadline deadline, boolean retry) {
            String result = request(messages, vision, true, deadline);
            JsonNode value = parseObject(result);
            if (!value.isObject()) throw new Failure("El modelo no devolvió un objeto JSON válido.", false);
            return new JsonResponse(value, result.length());
        }

        private String request(List<Map<String, Object>> messages, boolean vision, boolean structured, Deadline deadline) {
            var active = store.active(profile.owner());
            if (active == null || !active.id().equals(profile.id())
                    || !java.util.Arrays.equals(active.secret().nonce(), profile.secret().nonce())) {
                throw new ApiException(HttpStatus.CONFLICT, "El proveedor activo cambió. Vuelve a iniciar la operación.");
            }
            if (vision && !profile.vision()) throw new Failure("Este modelo no admite imágenes.", true);
            int estimate = estimate(messages);
            if (estimate > inputBudget()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El contenido no cabe en el contexto del modelo activo.");
            var limiter = limits.computeIfAbsent(profile.owner(), unused -> new Limits());
            limiter.begin(profile.concurrency(), false);
            try {
                var response = perform(active, messages, structured && active.jsonMode(), deadline.cap(Duration.ofSeconds(75)));
                int actual = response.body().path("usage").path("prompt_tokens").asInt(0);
                if (actual > estimate) tokenAdjustment = Math.min(4, Math.max(tokenAdjustment, (double)actual / estimate));
                String value = content(response);
                if (response.status() == 429) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "El proveedor alcanzó su límite de solicitudes.");
                if (value.isBlank()) throw new Failure("El proveedor no devolvió contenido utilizable.", false);
                return value;
            } finally {
                limiter.end();
            }
        }
    }

    private static final class Limits {
        private long minute = -1, hour = -1;
        private int generation, probes, concurrent;
        synchronized void begin(int maximum, boolean probe) {
            long time = System.currentTimeMillis();
            if (time / 60_000 != minute) { minute = time / 60_000; generation = 0; }
            if (time / 3_600_000 != hour) { hour = time / 3_600_000; probes = 0; }
            if (concurrent >= maximum || (probe ? probes >= 10 : generation >= 60)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Se alcanzó el límite de solicitudes de IA.");
            }
            if (probe) probes++; else generation++;
            concurrent++;
        }
        synchronized void end() { concurrent--; }
    }
}
