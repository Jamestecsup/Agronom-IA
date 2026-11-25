package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.user.UserAccounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agronomia-api/api")
class AiSettingsController {
    private final UserAccounts accounts;
    private final ProviderStore store;
    private final AiProviderResolver resolver;
    private final ObjectMapper mapper;

    AiSettingsController(UserAccounts accounts, ProviderStore store, AiProviderResolver resolver, ObjectMapper mapper) {
        this.accounts = accounts;
        this.store = store;
        this.resolver = resolver;
        this.mapper = mapper;
    }

    @GetMapping("/ai/presets")
    ResponseEntity<?> presets(@RequestHeader(name="Authorization", required=false) String token) {
        accounts.requireUser(token);
        return ok(Map.of("presets", List.of(
                Map.of("name", "Nemotron Lightning", "base_url", "https://openrouter.ai/api/v1",
                        "model", "nvidia/nemotron-3.5-lightning:free"),
                Map.of("name", "OpenAI", "base_url", "https://api.openai.com/v1", "model", "gpt-4o-mini"))));
    }

    @GetMapping("/ai/providers")
    ResponseEntity<?> providers(@RequestHeader(name="Authorization", required=false) String token) {
        long user = accounts.requireUser(token);
        var active = store.active(user);
        var body = mapper.createObjectNode();
        body.put("active_id", active == null ? null : active.id());
        var list = body.putArray("providers");
        store.list(user).forEach(p -> list.add(p.view(mapper)));
        return ok(body);
    }

    @PostMapping("/ai/providers")
    ResponseEntity<?> create(@RequestHeader(name="Authorization", required=false) String token, @RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(store.create(accounts.requireUser(token), body).view(mapper));
    }

    @PatchMapping("/ai/providers/{id}")
    ResponseEntity<?> edit(@RequestHeader(name="Authorization", required=false) String token, @PathVariable UUID id,
                           @RequestBody JsonNode body) {
        return ok(store.update(accounts.requireUser(token), id.toString(), body).view(mapper));
    }

    @DeleteMapping("/ai/providers/{id}")
    ResponseEntity<?> delete(@RequestHeader(name="Authorization", required=false) String token, @PathVariable UUID id) {
        store.delete(accounts.requireUser(token), id.toString());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/ai/providers/{id}/test")
    ResponseEntity<?> test(@RequestHeader(name="Authorization", required=false) String token, @PathVariable UUID id) {
        return ok(resolver.probe(accounts.requireUser(token), id.toString()).view(mapper));
    }

    @PutMapping("/ai/selection")
    ResponseEntity<?> selection(@RequestHeader(name="Authorization", required=false) String token, @RequestBody JsonNode body) {
        long user = accounts.requireUser(token);
        var id = body.path("provider_id");
        if (!id.isNull() && !id.isTextual()) throw new ApiException(HttpStatus.BAD_REQUEST, "Selección inválida.");
        String selected = null;
        if (!id.isNull()) {
            try { selected = UUID.fromString(id.asText()).toString(); }
            catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Selección inválida."); }
        }
        store.select(user, selected, body.path("consent").isBoolean() && body.path("consent").booleanValue());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/ai/capabilities")
    ResponseEntity<?> capabilities(@RequestHeader(name="Authorization", required=false) String token) {
        long user = accounts.requireUser(token);
        var profile = store.active(user);
        var body = mapper.createObjectNode();
        body.put("provider_id", profile == null ? null : profile.id());
        boolean vision = profile == null || profile.vision();
        int budget = profile == null ? Integer.MAX_VALUE : profile.context() - profile.output();
        var features = body.putObject("features");
        feature(features.putObject("card"), budget >= 700, "El contexto del modelo es demasiado pequeño.");
        feature(features.putObject("chat"), budget >= 500, "El contexto del modelo es demasiado pequeño.");
        feature(features.putObject("chat_photo"), vision && budget >= 1700, "El modelo requiere visión y más contexto.");
        feature(features.putObject("schedule_pdf"), vision && budget >= 1500, "El modelo requiere visión y más contexto.");
        feature(features.putObject("care_plan"), budget >= 700, "El contexto del modelo es demasiado pequeño.");
        return ok(body);
    }

    private static void feature(com.fasterxml.jackson.databind.node.ObjectNode node, boolean available, String reason) {
        node.put("available", available);
        node.put("reason", available ? "" : reason);
    }

    @GetMapping("/preferences")
    ResponseEntity<?> preferences(@RequestHeader(name="Authorization", required=false) String token) {
        return ok(Map.of("version",1,"preferences",store.preferences(accounts.requireUser(token))));
    }

    @PutMapping("/preferences")
    ResponseEntity<?> savePreferences(@RequestHeader(name="Authorization", required=false) String token, @RequestBody JsonNode body) {
        long user = accounts.requireUser(token);
        if (!body.path("version").isIntegralNumber() || body.path("version").intValue() != 1
                || body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Versión o tamaño de preferencias inválidos.");
        }
        store.savePreferences(user, body.path("preferences"));
        return ok(Map.of("version",1,"preferences",store.preferences(user)));
    }

    private static ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
