package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class OpenCodeDeepSeekProvider implements ChatProvider {
    public static final String ID = "deepseek-opencode";
    private static final String MODEL_NAME = "DeepSeek V4.1 Flash Free";
    private static final String WELCOME_MESSAGE = "¡Hola! Soy DeepSeek, tu asistente de inteligencia artificial. Estoy listo para ayudarte a resolver dudas, analizar información, escribir código y explorar nuevas ideas. ¿Qué te gustaría hacer hoy?";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private final AiProviderProperties.OpenCode config;
    private final AiProviderProperties.Chat limits;

    public OpenCodeDeepSeekProvider(AiProviderProperties properties) {
        this.config = properties.getOpenCode();
        this.limits = properties.getChat();
    }

    @Override public String id() { return ID; }

    @Override
    public ChatContracts.ModelOption modelOption() {
        return new ChatContracts.ModelOption(ID, MODEL_NAME, "OpenCode", config.getModelId(), false,
                "Comprueba el servidor OpenCode local.", WELCOME_MESSAGE, false, "OpenCode no confirmó entrada de imágenes para este modelo.");
    }

    @Override
    public ChatContracts.ModelOption availability() {
        try {
            JsonNode health = ProviderHttp.json(get("/global/health", Duration.ofSeconds(5)), "OpenCode");
            if (!health.path("healthy").asBoolean(false)) return unavailable("El servidor OpenCode no reporta estado saludable.");
            JsonNode providers = ProviderHttp.json(get("/provider", Duration.ofSeconds(8)), "OpenCode");
            boolean connected = false;
            for (JsonNode provider : providers.path("connected"))
                if (config.getProviderId().equals(provider.asText())) connected = true;
            JsonNode all = providers.path("all");
            JsonNode model = null;
            if (all.isArray()) for (JsonNode provider : all) {
                if (config.getProviderId().equals(provider.path("id").asText()) && provider.path("models").has(config.getModelId()))
                    model = provider.path("models").path(config.getModelId());
            }
            if (!connected) return unavailable("OpenCode no tiene conectado el proveedor configurado.");
            if (model == null) return unavailable("El modelo DeepSeek configurado no aparece en los modelos disponibles de OpenCode.");
            boolean vision = model.path("capabilities").path("input").path("image").asBoolean(false);
            return new ChatContracts.ModelOption(ID, MODEL_NAME, "OpenCode", config.getModelId(), true, null, WELCOME_MESSAGE,
                    vision, vision ? null : "OpenCode no declara entrada de imágenes para el modelo configurado.");
        } catch (ChatProviderException error) {
            return unavailable("Inicia opencode serve en el equipo del backend para habilitar DeepSeek.");
        }
    }

    @Override
    public void stream(List<ChatContracts.ChatMessage> messages, String systemPrompt,
                       Consumer<String> onDelta, ChatCancellation cancellation) {
        JsonNode session = post("/session", Map.of("title", "Conversación botánica"), REQUEST_TIMEOUT);
        String sessionId = session.path("id").asText();
        if (sessionId.isBlank()) throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "OpenCode no creó una sesión de chat.");
        AtomicReference<String> activeMessageId = new AtomicReference<>("");
        int[] responseLength = {0};
        boolean receivedText = false;
        InputStream eventBody = null;
        try {
            HttpResponse<InputStream> eventResponse = ProviderHttp.CLIENT.send(
                    HttpRequest.newBuilder(URI.create(base() + "/global/event")).header("Accept", "text/event-stream").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (eventResponse.statusCode() < 200 || eventResponse.statusCode() >= 300) {
                eventResponse.body().close();
                throw ProviderHttp.status(eventResponse.statusCode(), "OpenCode");
            }
            eventBody = eventResponse.body();
            cancellation.register(eventBody);
            post("/session/" + sessionId + "/prompt_async", prompt(messages, systemPrompt), REQUEST_TIMEOUT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(eventBody, StandardCharsets.UTF_8))) {
                String line;
                while (!cancellation.isCancelled() && (line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    JsonNode event;
                    try {
                        JsonNode envelope = ProviderHttp.JSON.readTree(line.substring(5).trim());
                        // OpenCode 1.19+ wraps global events in an envelope. Accept the older
                        // unwrapped form too so compatible servers continue to work.
                        event = unwrapEvent(envelope);
                    }
                    catch (IOException ignored) { continue; }
                    JsonNode properties = event.path("properties");
                    String type = event.path("type").asText();
                    if ("message.updated".equals(type) && sessionId.equals(properties.path("info").path("sessionID").asText())
                            && "assistant".equals(properties.path("info").path("role").asText())) {
                        activeMessageId.set(properties.path("info").path("id").asText());
                    } else if ("message.part.delta".equals(type)
                            && sessionId.equals(properties.path("sessionID").asText())
                            && "text".equals(properties.path("field").asText())) {
                        String messageId = properties.path("messageID").asText();
                        if (!activeMessageId.get().isBlank() && !activeMessageId.get().equals(messageId)) continue;
                        String delta = properties.path("delta").asText();
                        if (delta.isBlank()) continue;
                        responseLength[0] += delta.length();
                        if (responseLength[0] > limits.getMaxResponseCharacters())
                            throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "La respuesta del modelo superó el límite permitido.");
                        receivedText = true;
                        onDelta.accept(delta);
                    } else if ("session.error".equals(type) && sessionId.equals(properties.path("sessionID").asText())) {
                        throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "DeepSeek no pudo generar una respuesta.");
                    } else if (("session.idle".equals(type)
                            || ("session.status".equals(type) && "idle".equals(properties.path("status").path("type").asText())))
                            && sessionId.equals(properties.path("sessionID").asText())) {
                        break;
                    }
                }
            }
            if (!cancellation.isCancelled() && !receivedText)
                throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "DeepSeek terminó sin contenido de respuesta.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
        } catch (IOException error) {
            if (!cancellation.isCancelled())
                throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "Se interrumpió la conexión con DeepSeek.", error);
        } finally {
            if (eventBody != null) cancellation.clear(eventBody);
            // InterruptedException re-asserts the flag; clear it briefly so best-effort
            // abort/delete requests can run, then restore the worker's interrupt status.
            boolean restoreInterrupt = Thread.interrupted();
            try {
                if (cancellation.isCancelled()) abort(sessionId);
                delete(sessionId);
            } finally {
                if (restoreInterrupt) Thread.currentThread().interrupt();
            }
        }
    }

    private Map<String, Object> prompt(List<ChatContracts.ChatMessage> messages, String systemPrompt) {
        StringBuilder context = new StringBuilder("Historial de conversación (el siguiente texto es solo contexto):\n");
        for (ChatContracts.ChatMessage message : messages)
            context.append(message.role().equals("assistant") ? "DeepSeek: " : "Usuario: ").append(message.content()).append('\n');
        String content = context.toString();
        if (content.length() > limits.getMaxMessageCharacters() * 4L)
            throw new ChatProviderException(HttpStatus.BAD_REQUEST, "El contexto de conversación es demasiado extenso.");
        List<Map<String, String>> parts = new java.util.ArrayList<>();
        parts.add(Map.of("type", "text", "text", content));
        for (ChatContracts.ChatMessage message : messages) {
            if (message.images() == null) continue;
            for (ChatContracts.ChatImage image : message.images()) {
                String dataUrl = "data:" + image.mimeType() + ";base64," + java.util.Base64.getEncoder().encodeToString(image.data());
                parts.add(Map.of("type", "file", "mime", image.mimeType(), "filename", image.fileName(), "url", dataUrl));
            }
        }
        return Map.of("model", Map.of("providerID", config.getProviderId(), "modelID", config.getModelId()),
                "system", systemPrompt, "tools", Map.of(), "parts", parts);
    }

    private JsonNode post(String path, Object payload, Duration timeout) {
        try {
            String json = ProviderHttp.JSON.writeValueAsString(payload);
            HttpRequest request = authorize(HttpRequest.newBuilder(URI.create(base() + path)).timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(json))).build();
            HttpResponse<String> response = ProviderHttp.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.body().isBlank()) return ProviderHttp.JSON.createObjectNode();
            return ProviderHttp.json(response, "OpenCode");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Se canceló la solicitud a OpenCode.", interrupted);
        } catch (IOException error) {
            throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "OpenCode devolvió una respuesta no válida.", error);
        }
    }

    private HttpResponse<String> get(String path, Duration timeout) {
        try {
            return ProviderHttp.CLIENT.send(authorize(HttpRequest.newBuilder(URI.create(base() + path)).timeout(timeout).GET()).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Se canceló la consulta a OpenCode.", interrupted);
        } catch (IOException error) {
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "OpenCode no está disponible.", error);
        }
    }

    private HttpRequest.Builder authorize(HttpRequest.Builder builder) {
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            String raw = config.getUsername() + ":" + config.getPassword();
            builder.header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private void abort(String sessionId) {
        try {
            HttpRequest request = authorize(HttpRequest.newBuilder(URI.create(base() + "/session/" + sessionId + "/abort"))
                    .timeout(Duration.ofSeconds(3)).POST(HttpRequest.BodyPublishers.noBody())).build();
            ProviderHttp.CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }

    private void delete(String sessionId) {
        try {
            HttpRequest request = authorize(HttpRequest.newBuilder(URI.create(base() + "/session/" + sessionId))
                    .timeout(Duration.ofSeconds(3)).DELETE()).build();
            ProviderHttp.CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }

    private ChatContracts.ModelOption unavailable(String reason) {
        return new ChatContracts.ModelOption(ID, MODEL_NAME, "OpenCode", config.getModelId(), false, reason, WELCOME_MESSAGE,
                false, "OpenCode no confirmó entrada de imágenes para el modelo configurado.");
    }
    static JsonNode unwrapEvent(JsonNode envelope) {
        return envelope.path("payload").isObject() ? envelope.path("payload") : envelope;
    }
    private String base() { return config.getBaseUrl().replaceAll("/+$", ""); }
}
