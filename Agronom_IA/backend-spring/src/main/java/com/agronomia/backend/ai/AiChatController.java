package com.agronomia.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private final ChatOrchestrationService chatService;
    private final ThreadPoolTaskExecutor chatExecutor;

    public AiChatController(ChatOrchestrationService chatService, ThreadPoolTaskExecutor chatExecutor) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
    }

    @GetMapping("/models")
    public Map<String, Object> models() {
        return Map.of("models", chatService.availableModels(), "selectionPolicy", "per-message-with-conversation-context");
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> chat(@RequestBody(required = false) ChatContracts.ChatRequest request) {
        String invalid = chatService.validate(request);
        if (invalid != null) return jsonError(HttpStatus.BAD_REQUEST, invalid);

        String unavailable = chatService.providerUnavailableMessage(request.providerId());
        if (unavailable != null) return jsonError(HttpStatus.SERVICE_UNAVAILABLE, unavailable);

        SseEmitter emitter = new SseEmitter(210_000L);
        ChatCancellation cancellation = new ChatCancellation();
        AtomicBoolean finished = new AtomicBoolean();
        AtomicReference<Thread> worker = new AtomicReference<>();
        Runnable cancel = () -> {
            if (!finished.get()) {
                cancellation.cancel();
                Thread active = worker.get();
                if (active != null) active.interrupt();
            }
        };
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());
        emitter.onCompletion(cancel);

        try {
            chatExecutor.execute(() -> {
                worker.set(Thread.currentThread());
                try {
                    chatService.stream(request, delta -> send(emitter, "delta", new ChatContracts.Delta(delta)), cancellation);
                    if (cancellation.isCancelled()) return;
                    send(emitter, "done", new ChatContracts.Completion(request.providerId()));
                    finished.set(true);
                    emitter.complete();
                } catch (Exception error) {
                    if (!cancellation.isCancelled()) {
                        String message = error instanceof ChatProviderException providerError
                                ? providerError.getMessage() : "No se pudo completar la respuesta del asistente.";
                        if (!(error instanceof ChatProviderException)) log.warn("Chat provider failed ({})", error.getClass().getSimpleName());
                        try { send(emitter, "error", new ChatContracts.ChatError(message)); }
                        catch (RuntimeException ignored) { }
                    }
                    finished.set(true);
                    emitter.complete();
                }
            });
        } catch (TaskRejectedException rejected) {
            finished.set(true);
            return jsonError(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de chat está ocupado. Inténtalo de nuevo en unos instantes.");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    private void send(SseEmitter emitter, String name, Object payload) {
        try { emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON)); }
        catch (IOException error) { throw new IllegalStateException("El cliente cerró la conexión de chat.", error); }
    }

    private ResponseEntity<ChatContracts.ChatError> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ChatContracts.ChatError(message));
    }
}
