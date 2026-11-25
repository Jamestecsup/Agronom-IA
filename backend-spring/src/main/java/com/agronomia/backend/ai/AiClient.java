package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface AiClient {
    String reply(String step, List<Map<String, Object>> messages, boolean vision);
    JsonNode json(String step, List<Map<String, Object>> messages, boolean vision);

    default AiClient forUser(long user) { return this; }
    default boolean supportsVision() { return true; }
    default int inputBudget() { return Integer.MAX_VALUE; }
    default int outputBudget() { return Integer.MAX_VALUE; }
    default int concurrency() { return 1; }
    default int estimateCharacters(int characters) { return (int)Math.ceil(characters / 3.0 * 1.2); }
    default int estimate(List<Map<String, Object>> messages) {
        int total = 4;
        for (var message : messages) {
            total += 8;
            Object content = message.get("content");
            if (content instanceof List<?> parts) {
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> item) {
                        if ("image_url".equals(item.get("type"))) total += 1024;
                        else if (item.get("text") instanceof String text) total += estimateCharacters(text.length());
                    }
                }
            }
        }
        return total;
    }

    default JsonResponse jsonWithin(String step, List<Map<String, Object>> messages, boolean vision,
                                    Deadline deadline, boolean retry) {
        deadline.requireTime();
        JsonNode value = json(step, messages, vision);
        return new JsonResponse(value, value.toString().length());
    }

    default JsonResponse auxiliaryJsonWithin(String step, List<Map<String, Object>> messages,
                                             Deadline deadline, boolean retry) {
        return jsonWithin(step, messages, false, deadline, retry);
    }

    record JsonResponse(JsonNode value, int characters) {}

    /** Plazo monotónico que puede compartirse entre llamadas en paralelo. */
    record Deadline(long expiresAt) {
        public static Deadline after(Duration duration) {
            return new Deadline(System.nanoTime() + duration.toNanos());
        }

        public Deadline cap(Duration duration) {
            return new Deadline(Math.min(expiresAt, System.nanoTime() + duration.toNanos()));
        }

        public long remainingNanos() {
            return Math.max(0, expiresAt - System.nanoTime());
        }

        public void requireTime() {
            if (remainingNanos() == 0) throw new Failure("Se agotó el tiempo de la consulta a la IA.", false);
        }
    }

    static Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "content", List.of(Map.of("type", "text", "text", text)));
    }

    final class Failure extends RuntimeException {
        private final boolean visionUnsupported;
        public Failure(String message, boolean visionUnsupported) {
            super(message);
            this.visionUnsupported = visionUnsupported;
        }
        public boolean visionUnsupported() { return visionUnsupported; }
    }
}
