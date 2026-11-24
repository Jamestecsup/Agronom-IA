package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class PlantCards {
    private static final Logger LOG = LoggerFactory.getLogger(PlantCards.class);
    private static final Duration MIN_OPTIONAL = Duration.ofSeconds(5);
    private static final Map<String, String> ALIASES = Map.of(
            "descripcion", "description", "luz", "light", "riego", "watering", "suelo", "soil",
            "clima", "climate", "floracion", "flowering", "usos", "uses", "cuidados", "care");
    private static final List<String> KEYS = List.of(
            "description", "light", "watering", "soil", "climate", "flowering", "uses", "care");
    private static final List<String> TITLES = List.of(
            "Descripción", "Luz", "Riego", "Suelo", "Clima", "Floración", "Usos", "Cuidados");
    private static final Set<String> MATERIAL_KEYS = Set.of("light", "watering", "soil", "climate", "care");
    private static final Set<String> UNKNOWN = Set.of("unknown", "desconocido", "desconocida",
            "no disponible", "n/a", "no se conoce", "sin informacion", "sin información", "no sé", "no se");
    private static final String FALLBACK = "No hay información fiable para esta categoría.";
    private static final String CONDITIONS = " El cuidado puede variar según el clima y la variedad.";
    public static final String INSUFFICIENT = "Identificamos la especie, pero no encontramos información suficiente sobre sus cuidados.";

    private final ObjectMapper mapper;
    private final AiClient ai;
    private final ExecutorService enrichment = Executors.newFixedThreadPool(4);

    public PlantCards(ObjectMapper mapper, AiClient ai) {
        this.mapper = mapper;
        this.ai = ai;
    }

    @jakarta.annotation.PreDestroy
    void stop() { enrichment.shutdownNow(); }

    public List<JsonNode> ranked(JsonNode raw) {
        var results = new ArrayList<JsonNode>();
        raw.path("results").forEach(results::add);
        results.sort(Comparator.comparingDouble((JsonNode row) -> row.path("score").asDouble()).reversed());
        return results;
    }

    public ObjectNode header(JsonNode row) {
        JsonNode species = row.path("species");
        return mapper.createObjectNode()
                .put("common_name", species.path("commonNames").path(0).asText(""))
                .put("scientific_name", scientific(row))
                .put("authorship", species.path("scientificNameAuthorship").asText(""))
                .put("family", species.path("family").path("scientificNameWithoutAuthor").asText(""))
                .put("confidence", row.path("score").asDouble());
    }

    public String scientific(JsonNode row) {
        JsonNode species = row.path("species");
        return species.path("scientificNameWithoutAuthor").asText(species.path("scientificName").asText(""));
    }

    public ArrayNode candidates(List<JsonNode> rows, boolean hints, AiClient.Deadline deadline) {
        ArrayNode output = mapper.createArrayNode();
        for (JsonNode row : rows) {
            ObjectNode item = header(row);
            item.remove(List.of("common_name", "authorship"));
            item.set("common_names", array(row.path("species").path("commonNames")));
            output.add(item);
        }
        if (!hints) return output;
        JsonNode details = optional("candidate-hints", skill("candidate-hints") + """
                Devuelve un objeto con una clave por nombre científico y valores
                {"hint":"pista visual","terms":[{"word":"término","meaning":"definición"}]}.
                """, output.toString(), deadline, 20, false);
        for (JsonNode candidate : output) {
            ObjectNode item = (ObjectNode) candidate;
            JsonNode found = details.path(item.path("scientific_name").asText());
            item.put("hint", limit(clean(found.path("hint")), 800));
            item.set("terms", sanitized(found.path("terms"), "word", "meaning", 80, 300, 6));
        }
        return output;
    }

    public ObjectNode generate(JsonNode species, AiClient.Deadline deadline) {
        ObjectNode plant = header(species);
        String fact = "Ficha de Pl@ntNet:\n" + plant + "\nReferencias taxonómicas:\n" + species.path("species");
        String refined = optional("refine", skill("refine-prompt"), fact, deadline, 25, true).path("prompt").asText("");
        String categoryRules = KEYS.stream().map(key -> "### " + key + "\n" + skill("categories/" + key))
                .collect(Collectors.joining("\n"));
        ObjectNode shape = mapper.createObjectNode();
        KEYS.forEach(key -> shape.put(key, "texto de la categoría"));
        String system = """
                Genera una ficha botánica en español. Lee las instrucciones de contenido para las ocho categorías.
                """ + categoryRules + """

                Devuelve un objeto JSON con las ocho claves obligatorias del ejemplo, cada una con un texto
                de 4 a 6 frases. Da la mejor guía general con rangos y condiciones. Escribe "unknown" solo
                si no conoces la especie. Ejemplo de forma obligatoria:
                """ + shape;
        String input = fact + "\n" + refined;
        AiClient.Deadline categoryDeadline = deadline.cap(Duration.ofSeconds(90));
        ObjectNode received = categories(system, input, categoryDeadline, true);
        if (received.size() <= 4) {
            if (categoryDeadline.remainingNanos() < MIN_OPTIONAL.toNanos()) throw new NoPlantInformation();
            try {
                received = categories(system + """

                        Corrección obligatoria: escribe siempre la mejor guía general con sus condiciones.
                        Usa "unknown" solo si no conoces la especie. Completa las ocho claves en un único JSON.
                        """, input, categoryDeadline, false);
            } catch (AiClient.Failure error) {
                throw new NoPlantInformation();
            }
            if (received.size() <= 4) throw new NoPlantInformation();
        }
        ObjectNode texts = received;
        ObjectNode materialTexts = mapper.createObjectNode();
        for (String key : MATERIAL_KEYS) {
            if (texts.has(key)) materialTexts.set(key, texts.get(key));
        }
        CompletableFuture<JsonNode> glossary = optionalAsync("glossary", skill("glossary")
                + "\nDevuelve por categoría listas de objetos word y meaning.", texts.toString(), deadline);
        CompletableFuture<JsonNode> materials = materialTexts.isEmpty()
                ? CompletableFuture.completedFuture(mapper.createObjectNode())
                : optionalAsync("materials", skill("materials")
                    + "\nDevuelve por categoría listas de objetos name y detail.",
                    plant.path("scientific_name").asText() + "\n" + materialTexts, deadline);
        JsonNode g = glossary.join();
        JsonNode m = materials.join();
        ArrayNode sections = plant.putArray("sections");
        for (int i = 0; i < KEYS.size(); i++) {
            String key = KEYS.get(i);
            ObjectNode section = sections.addObject().put("key", key).put("title", TITLES.get(i))
                    .put("body", texts.path(key).asText(FALLBACK) + CONDITIONS);
            section.set("terms", sanitized(g.path(key), "word", "meaning", 80, 300, 12));
            section.set("materials", sanitized(m.path(key), "name", "detail", 120, 400, 10));
        }
        return plant;
    }

    public String skill(String id) {
        try {
            return new ClassPathResource("skills/" + id + ".md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Falta una instrucción de IA en el paquete.", error);
        }
    }

    private AiClient.JsonResponse ask(String step, String system, String user,
                                      AiClient.Deadline deadline, boolean retry) {
        return ai.jsonWithin(step, List.of(AiClient.message("system", system), AiClient.message("user", user)),
                false, deadline, retry);
    }

    private ObjectNode categories(String system, String input, AiClient.Deadline deadline, boolean retry) {
        AiClient.JsonResponse response = ask("categories", system, input, deadline, retry);
        Map<String, JsonNode> values = new LinkedHashMap<>();
        response.value().fields().forEachRemaining(entry -> {
            String name = Normalizer.normalize(entry.getKey().strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            String key = ALIASES.getOrDefault(name, name);
            if (KEYS.contains(key)) values.merge(key, entry.getValue(),
                    (first, second) -> flatten(first).isBlank() ? second : first);
        });
        Set<String> missing = new TreeSet<>(KEYS);
        missing.removeAll(values.keySet());
        Set<String> unknown = new TreeSet<>();
        Set<String> nonTextual = new TreeSet<>();
        ObjectNode result = mapper.createObjectNode();
        values.forEach((key, value) -> {
            if (!value.isTextual()) nonTextual.add(key);
            if (isUnknown(value)) unknown.add(key);
            String text = flatten(value);
            if (!text.isBlank()) result.put(key, text);
        });
        LOG.info("IA categories caracteres={} claves_faltantes={} claves_unknown={} claves_no_textuales={}",
                response.characters(), missing, unknown, nonTextual);
        return result;
    }

    private JsonNode optional(String step, String system, String user, AiClient.Deadline deadline,
                              int seconds, boolean retry) {
        if (deadline.remainingNanos() < MIN_OPTIONAL.toNanos()) {
            LOG.info("IA paso={} omitido por plazo restante", step);
            return mapper.createObjectNode();
        }
        try {
            return ask(step, system, user, deadline.cap(Duration.ofSeconds(seconds)), retry).value();
        } catch (AiClient.Failure error) {
            return mapper.createObjectNode();
        }
    }

    private CompletableFuture<JsonNode> optionalAsync(String step, String system, String user,
                                                      AiClient.Deadline deadline) {
        return CompletableFuture.supplyAsync(() -> optional(step, system, user, deadline, 30, true), enrichment)
                .completeOnTimeout(mapper.createObjectNode(), deadline.remainingNanos(), TimeUnit.NANOSECONDS);
    }

    private static String flatten(JsonNode node) {
        if (node.isTextual()) return clean(node);
        if (node.isNumber() || node.isBoolean()) return node.asText();
        if (!node.isContainerNode()) return "";
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            String text = flatten(child);
            if (!text.isBlank()) values.add(text);
        }
        return String.join("\n", values);
    }

    private static boolean isUnknown(JsonNode value) {
        return value.isTextual() && UNKNOWN.contains(
                value.asText().strip().toLowerCase(Locale.ROOT).replaceFirst("\\.+$", ""));
    }

    private JsonNode array(JsonNode input) {
        return input.isArray() ? input : mapper.createArrayNode();
    }

    private ArrayNode sanitized(JsonNode entries, String a, String b, int la, int lb, int count) {
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode item : array(entries)) {
            String left = clean(item.path(a));
            String right = clean(item.path(b));
            if (!left.isBlank() && !right.isBlank()) result.addObject().put(a, limit(left, la)).put(b, limit(right, lb));
            if (result.size() == count) break;
        }
        return result;
    }

    private static String clean(JsonNode node) {
        String text = node.isTextual() ? node.asText().strip() : "";
        String normalized = text.toLowerCase(Locale.ROOT).replaceFirst("\\.+$", "");
        return UNKNOWN.contains(normalized) ? "" : text;
    }

    private static String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public static final class NoPlantInformation extends RuntimeException {}
}
