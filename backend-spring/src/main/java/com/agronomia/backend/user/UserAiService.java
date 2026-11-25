package com.agronomia.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
public class UserAiService implements PlantAiClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Path skillsDir;

    public UserAiService(ObjectMapper json,
                         @Value("${ai.api-key:${AI_API_KEY:}}") String apiKey,
                         @Value("${ai.base-url:${AI_BASE_URL:https://generativelanguage.googleapis.com/v1beta/openai/}}") String baseUrl,
                         @Value("${ai.model:${AI_MODEL:gemini-3.5-flash-lite}}") String model,
                         @Value("${skills.dir:${SKILLS_DIR:backend-django/skills}}") String skillsDir) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.model = model;
        Path p = Path.of(skillsDir);
        if (!p.isAbsolute()) {
            // intenta relativo al cwd y al directorio padre (backend-spring/..)
            Path cwd = Path.of(System.getProperty("user.dir"));
            Path candidate = cwd.resolve(p).normalize();
            if (!Files.exists(candidate)) candidate = cwd.resolve("..").resolve(p).normalize();
            p = candidate;
        }
        this.skillsDir = p;
    }

    public String model() { return model; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }

    public String loadSkill(String name) {
        Path path = skillsDir.resolve(name + ".md");
        try {
            String text = Files.readString(path).trim();
            if (text.startsWith("---")) {
                int end = text.indexOf("---", 3);
                if (end > 0) text = text.substring(end + 3).trim();
            }
            if (text.isBlank()) throw new RuntimeException("Skill vacía: " + name);
            return text;
        } catch (IOException e) {
            throw new RuntimeException("Skill no encontrada: " + name, e);
        }
    }

    public String loadCategorySkill(String category) { return loadSkill("categories/" + category); }

    public Map<String, Object> jsonCall(String system, String user, double temperature) {
        if (apiKey.isBlank()) throw new RuntimeException("AI_API_KEY no configurada.");
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", system))),
                Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", user)))
        ));
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String req = json.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(90))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(req))
                        .build();
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 429) {
                    if (attempt == 0) { Thread.sleep(8000); continue; }
                    throw new RuntimeException("La IA alcanzó su límite de uso. Espera unos minutos y vuelve a intentarlo.");
                }
                if (resp.statusCode() != 200) throw new RuntimeException("IA HTTP " + resp.statusCode());
                Map<?,?> data = json.readValue(resp.body(), Map.class);
                List<?> choices = (List<?>) data.get("choices");
                if (choices == null || choices.isEmpty()) throw new RuntimeException("La IA no devolvió contenido.");
                Map<?,?> msg = (Map<?,?>) ((Map<?,?>) choices.get(0)).get("message");
                Object contentObj = msg == null ? "" : msg.get("content");
                String content = contentObj == null ? "" : String.valueOf(contentObj);
                return parseJson(content);
            } catch (Exception e) {
                last = e instanceof RuntimeException re ? re : new RuntimeException(e.getMessage(), e);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        throw last == null ? new RuntimeException("Error de IA") : last;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJson(String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new RuntimeException("La IA no devolvió un JSON válido.");
        try { return json.readValue(text.substring(start, end + 1), Map.class); }
        catch (Exception e) { throw new RuntimeException("La IA devolvió un JSON inválido.", e); }
    }

    public String chatReply(List<Map<String, Object>> messages, double temperature) {
        if (apiKey.isBlank()) throw new RuntimeException("AI_API_KEY no configurada.");
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", messages);
        try {
            String req = json.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(req))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("IA HTTP " + resp.statusCode());
            Map<?,?> data = json.readValue(resp.body(), Map.class);
            List<?> choices = (List<?>) data.get("choices");
            Map<?,?> msg = (Map<?,?>) ((Map<?,?>) choices.get(0)).get("message");
            Object contentObj = msg == null ? "" : msg.get("content");
            return contentObj == null ? "" : String.valueOf(contentObj).trim();
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new RuntimeException(e.getMessage(), e);
        }
    }
}
