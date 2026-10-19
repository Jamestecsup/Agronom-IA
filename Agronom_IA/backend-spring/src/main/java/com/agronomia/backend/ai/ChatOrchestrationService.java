package com.agronomia.backend.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class ChatOrchestrationService {
    private final Map<String, ChatProvider> providers;
    private final BotanyInstructions instructions;
    private final AiProviderProperties properties;

    public ChatOrchestrationService(List<ChatProvider> providers, BotanyInstructions instructions,
                                    AiProviderProperties properties) {
        Map<String, ChatProvider> byId = new ConcurrentHashMap<>();
        providers.forEach(provider -> byId.put(provider.id(), provider));
        this.providers = Map.copyOf(byId);
        this.instructions = instructions;
        this.properties = properties;
    }

    public List<ChatContracts.ModelOption> availableModels() {
        return List.of("space-bunny", "qwen-local").stream()
                .map(providers::get)
                .map(ChatProvider::availability)
                .toList();
    }

    public String validate(ChatContracts.ChatRequest request) {
        if (request == null || request.providerId() == null || request.messages() == null || request.messages().isEmpty())
            return "Incluye el modelo y al menos un mensaje.";
        if (!providers.containsKey(request.providerId())) return "El modelo solicitado no está autorizado.";
        if (request.messages().size() > properties.getChat().getMaxMessages())
            return "La conversación excede el límite de mensajes por solicitud.";
        long total = 0;
        for (ChatContracts.ChatMessage message : request.messages()) {
            if (message == null || message.role() == null || !(message.role().equals("user") || message.role().equals("assistant")))
                return "Cada mensaje debe tener rol user o assistant.";
            if (message.content() == null || message.content().isBlank()) return "Los mensajes no pueden estar vacíos.";
            if (message.content().length() > properties.getChat().getMaxMessageCharacters())
                return "Un mensaje excede el máximo permitido de caracteres.";
            total += message.content().length();
        }
        if (total > properties.getChat().getMaxMessageCharacters() * 4L)
            return "El contexto de conversación es demasiado extenso. Inicia una conversación nueva.";
        return null;
    }

    public String providerUnavailableMessage(String providerId) {
        ChatContracts.ModelOption model = providers.get(providerId).availability();
        return model.available() ? null : model.unavailableReason();
    }

    public void stream(ChatContracts.ChatRequest request, Consumer<String> onDelta, ChatCancellation cancellation) {
        ChatProvider provider = providers.get(request.providerId());
        String unavailable = providerUnavailableMessage(request.providerId());
        if (unavailable != null) throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, unavailable);
        AtomicInteger responseCharacters = new AtomicInteger();
        provider.stream(request.messages(), instructions.content(), delta -> {
            int total = responseCharacters.addAndGet(delta.length());
            if (total > properties.getChat().getMaxResponseCharacters())
                throw new ChatProviderException(HttpStatus.BAD_GATEWAY, "La respuesta del modelo superó el límite permitido.");
            onDelta.accept(delta);
        }, cancellation);
    }
}
