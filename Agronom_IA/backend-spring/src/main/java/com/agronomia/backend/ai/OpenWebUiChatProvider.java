package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OpenWebUiChatProvider implements ChatProvider {
    public static final String ID = "qwen-local";
    private final AiProviderProperties.OpenWebUi config;
    private final AiProviderProperties.Chat chatLimits;

    public OpenWebUiChatProvider(AiProviderProperties properties) {
        this.config = properties.getOpenWebUi();
        this.chatLimits = properties.getChat();
    }

    @Override public String id() { return ID; }

    @Override
    public ChatContracts.ModelOption modelOption() {
        return new ChatContracts.ModelOption(ID, "Qwen 3.6 35B A3B FP8", "Open WebUI", config.getModel(),
                false, "Comprueba la conexión con Open WebUI.");
    }

    @Override
    public ChatContracts.ModelOption availability() {
        if (config.getApiKey() == null || config.getApiKey().isBlank())
            return unavailable("Falta OPENWEBUI_API_KEY en el entorno del backend.");
        try {
            JsonNode models = ProviderHttp.json(ProviderHttp.get(base() + "/api/models", Duration.ofSeconds(8), bearer()), "Open WebUI");
            JsonNode data = models.path("data");
            if (!data.isArray()) return unavailable("Open WebUI devolvió una lista de modelos no válida.");
            for (JsonNode model : data) {
                if (config.getModel().equals(model.path("id").asText()))
                    return new ChatContracts.ModelOption(ID, "Qwen 3.6 35B A3B FP8", "Open WebUI", config.getModel(), true, null);
            }
            return unavailable("El modelo configurado no aparece en Open WebUI.");
        } catch (ChatProviderException error) {
            return unavailable(error.getMessage());
        }
    }

    @Override
    public void stream(List<ChatContracts.ChatMessage> messages, String systemPrompt,
                       Consumer<String> onDelta, ChatCancellation cancellation) {
        if (config.getApiKey() == null || config.getApiKey().isBlank())
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Open WebUI no está configurado en el backend.");
        List<Map<String, String>> context = new ArrayList<>();
        context.add(Map.of("role", "system", "content", systemPrompt));
        messages.forEach(message -> context.add(Map.of("role", message.role(), "content", message.content())));
        Map<String, Object> payload = Map.of("model", config.getModel(), "messages", context, "stream", true);
        final String body;
        try { body = ProviderHttp.JSON.writeValueAsString(payload); }
        catch (IOException error) { throw new ChatProviderException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar la solicitud de chat.", error); }

        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/api/chat/completions"))
                .timeout(config.getReadTimeout())
                .header("Authorization", bearer())
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<java.io.InputStream> response;
        try { response = ProviderHttp.CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream()); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Se canceló la generación de Open WebUI.", interrupted);
        } catch (IOException error) {
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo conectar con Open WebUI.", error);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try { response.body().close(); } catch (IOException ignored) { }
            throw ProviderHttp.status(response.statusCode(), "Open WebUI");
        }

        var stream = response.body();
        cancellation.register(stream);
        int totalCharacters = 0;
        boolean receivedText = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while (!cancellation.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;
                JsonNode event;
                try { event = ProviderHttp.JSON.readTree(data); }
                catch (IOException ignored) { continue; }
                JsonNode content = event.path("choices").path(0).path("delta").path("content");
                String text = contentText(content);
                if (text.isEmpty()) continue;
                totalCharacters += text.length();
                if (totalCharacters > chatLimits.getMaxResponseCharacters())
                    throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "La respuesta del modelo superó el límite permitido.");
                receivedText = true;
                onDelta.accept(text);
            }
        } catch (IOException error) {
            if (!cancellation.isCancelled())
                throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "Se interrumpió la respuesta de Open WebUI.", error);
        } finally {
            cancellation.clear(stream);
        }
        if (!cancellation.isCancelled() && !receivedText)
            throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "Open WebUI terminó sin contenido de respuesta.");
    }

    private String contentText(JsonNode content) {
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            StringBuilder value = new StringBuilder();
            content.forEach(part -> value.append(part.path("text").asText("")));
            return value.toString();
        }
        return "";
    }

    private ChatContracts.ModelOption unavailable(String reason) {
        return new ChatContracts.ModelOption(ID, "Qwen 3.6 35B A3B FP8", "Open WebUI", config.getModel(), false, reason);
    }

    private String base() { return config.getBaseUrl().replaceAll("/+$", ""); }
    private String bearer() { return "Bearer " + config.getApiKey(); }
}
