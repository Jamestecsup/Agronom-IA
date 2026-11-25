package com.agronomia.backend.schedule;

import com.agronomia.backend.ai.AiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ScheduleAiService {
    private static final long MAX_PDF_BYTES = 15L * 1024 * 1024;
    private static final int MAX_PAGES = 5;
    private static final int MAX_CLASSES = 100;
    private static final int MAX_IMAGE_EDGE = 1600;

    private final ObjectMapper mapper;
    private final AiClient ai;

    public ScheduleAiService(ObjectMapper mapper, AiClient ai) {
        this.mapper = mapper;
        this.ai = ai;
    }

    public JsonNode parseWeeklySchedule(MultipartFile pdf) {
        return parseWeeklySchedule(pdf, ai);
    }

    public JsonNode parseWeeklySchedule(MultipartFile pdf, long user) {
        return parseWeeklySchedule(pdf, ai.forUser(user));
    }

    private JsonNode parseWeeklySchedule(MultipartFile pdf, AiClient client) {
        if (pdf == null || pdf.isEmpty() || pdf.getSize() > MAX_PDF_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El PDF está vacío o supera 15 MB.");
        }
        if (!client.supportsVision()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El modelo activo no admite imágenes de PDF.");
        try (PDDocument document = Loader.loadPDF(pdf.getBytes())) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1 || document.getNumberOfPages() > MAX_PAGES) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El horario debe tener entre 1 y 5 páginas y no estar protegido.");
            }
            int budget = client.inputBudget();
            int availablePages = budget == Integer.MAX_VALUE ? MAX_PAGES : (budget - 440) / 1024;
            if (document.getNumberOfPages() > availablePages) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El horario no cabe en el contexto del modelo activo. Reduce el número de páginas.");
            }
            String text = new PDFTextStripper().getText(document);
            PDFRenderer renderer = new PDFRenderer(document);
            ArrayNode images = mapper.createArrayNode();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, budget == Integer.MAX_VALUE ? 110 : 85, ImageType.RGB);
                image = resizeIfNeeded(image);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", output);
                ObjectNode imagePart = mapper.createObjectNode();
                imagePart.put("type", "image_url");
                imagePart.putObject("image_url").put(
                        "url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
                );
                images.add(imagePart);
                image.flush();
            }

            ObjectNode prompt = mapper.createObjectNode();
            prompt.put("type", "text");
            prompt.put("text", "Extrae el horario semanal de clases de este PDF. El texto extraído ayuda a leer " +
                    "códigos y nombres; las imágenes conservan la posición de cada bloque en la grilla. " +
                    "Usa solo bloques visibles dentro de Lunes a Domingo. No incluyas tareas, entregas, vencimientos, " +
                    "leyendas de impresión ni cursos que no tengan bloque horario. Devuelve SOLO JSON: " +
                    "{\"classes\":[{\"course_code\":\"\",\"course_name\":\"\",\"day_of_week\":1," +
                    "\"start_time\":\"08:00\",\"end_time\":\"10:30\",\"section\":\"\"}]}. " +
                    "day_of_week usa ISO 1=lunes ... 7=domingo; horas en formato 24h HH:mm. " +
                    "Si el código se ve en la grilla y el nombre solo aparece en la leyenda inferior, relaciónalos. " +
                    "No inventes datos. Texto extraído del PDF:\n" + truncate(text, budget == Integer.MAX_VALUE
                            ? 45_000 : Math.max(0, (budget - 440 - 1024 * document.getNumberOfPages()))));
            images.insert(0, prompt);

            JsonNode result = callAi(client,
                    "Eres un extractor de horarios académicos. Lee texto e imagen conjuntamente y produce únicamente JSON válido.",
                    images, true
            );
            ArrayNode classes = normalizeClasses(result.path("classes"));
            if (classes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No se detectaron bloques de clase. Usa el PDF oficial con la grilla visible.");
            }
            ObjectNode response = mapper.createObjectNode();
            response.set("classes", classes);
            return response;
        } catch (ResponseStatusException | com.agronomia.backend.images.ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo leer el PDF o analizar el horario con la IA.", error);
        }
    }

    public JsonNode draftCarePlan(JsonNode request) {
        return draftCarePlan(request, ai);
    }

    public JsonNode draftCarePlan(JsonNode request, long user) {
        return draftCarePlan(request, ai.forUser(user));
    }

    private JsonNode draftCarePlan(JsonNode request, AiClient client) {
        JsonNode classes = request.path("classes");
        JsonNode plants = request.path("plants");
        if (!classes.isArray() || classes.isEmpty() || !plants.isArray() || plants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se necesita un horario confirmado y al menos una planta.");
        }
        if (client.inputBudget() == Integer.MAX_VALUE) return careBatch(client, classes, plants);
        var compactSchedule = mapper.createArrayNode();
        classes.forEach(row -> compactSchedule.addObject().put("day_of_week", row.path("day_of_week").asInt())
                .put("start_time",row.path("start_time").asText()).put("end_time",row.path("end_time").asText()));
        int remaining = client.inputBudget() - 480 - client.estimateCharacters(compactSchedule.toString().length());
        if (remaining < 120) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El horario ocupa demasiado contexto para generar el plan de cuidados.");
        int batchSize = Math.min(4, Math.max(1, remaining / 450));
        int charLimit = Math.max(80, Math.min(1800, (remaining / batchSize - 70)));
        var tasks = mapper.createArrayNode();
        var batch = mapper.createArrayNode();
        for (JsonNode plant : plants) {
            var entry = plant.deepCopy();
            if (entry instanceof ObjectNode object) object.put("care_information", truncate(entry.path("care_information").asText(), charLimit));
            batch.add(entry);
            if (batch.size() == batchSize) {
                tasks.addAll((ArrayNode)careBatch(client, compactSchedule, batch).path("tasks"));
                batch.removeAll();
            }
        }
        if (!batch.isEmpty()) tasks.addAll((ArrayNode)careBatch(client, compactSchedule, batch).path("tasks"));
        return mapper.createObjectNode().set("tasks",tasks);
    }

    private JsonNode careBatch(AiClient client, JsonNode classes, JsonNode plants) {
        ObjectNode prompt = mapper.createObjectNode();
        prompt.put("type", "text");
        prompt.put("text", "Propón acciones recurrentes semanales de cuidado para estas plantas, respetando sus indicaciones " +
                "botánicas y colocando cada acción solo fuera de los bloques de clase. Las clases son bloques ocupados " +
                "con inicio y fin. No uses tareas ni entregas de Canvas. No inventes frecuencias de riego: cuando la ficha " +
                "no indique una frecuencia segura, formula una acción de observación (por ejemplo, revisar humedad) y " +
                "explica qué criterio seguir. Máximo 3 acciones por planta en la semana. Devuelve SOLO JSON: " +
                "{\"tasks\":[{\"plant_id\":\"id recibido\",\"title\":\"acción breve\",\"instructions\":\"pasos concretos basados en la ficha\",\"day_of_week\":1,\"start_time\":\"08:00\",\"duration_minutes\":20}]}. " +
                "Usa días ISO 1=lunes ... 7=domingo y horas 24h. Si no hay tarea segura, devuelve lista vacía. " +
                "Datos de entrada:\nCLASES=" + classes.toString() + "\nPLANTAS=" + plants.toString());
        ArrayNode content = mapper.createArrayNode().add(prompt);
        JsonNode result = callAi(client, "Eres un agrónomo que organiza rutinas semanales sin cruzarlas con clases.", content, false);
        ArrayNode tasks = normalizeTasks(result.path("tasks"), classes, plants);
        ObjectNode response = mapper.createObjectNode();
        response.set("tasks", tasks);
        return response;
    }

    private JsonNode callAi(AiClient client, String system, ArrayNode userContent, boolean vision) {
        try {
            List<Map<String, Object>> parts = mapper.convertValue(userContent, new TypeReference<>() {});
            return client.json(vision ? "schedule-import" : "care-plan",
                    List.of(AiClient.message("system", system), Map.of("role", "user", "content", parts)), vision);
        } catch (AiClient.Failure error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage());
        }
    }

    private ArrayNode normalizeClasses(JsonNode input) {
        ArrayNode result = mapper.createArrayNode();
        Set<String> unique = new HashSet<>();
        if (!input.isArray()) return result;
        for (JsonNode item : input) {
            if (result.size() >= MAX_CLASSES) break;
            int day = item.path("day_of_week").asInt(0);
            String start = normalizeTime(item.path("start_time").asText(""));
            String end = normalizeTime(item.path("end_time").asText(""));
            String code = clean(item.path("course_code").asText(item.path("code").asText("")), 32);
            String name = clean(item.path("course_name").asText(item.path("name").asText("")), 180);
            String section = clean(item.path("section").asText(""), 80);
            if (day < 1 || day > 7 || start.isBlank() || end.isBlank() || name.isBlank()) continue;
            if (!LocalTime.parse(start).isBefore(LocalTime.parse(end))) continue;
            String key = day + "|" + start + "|" + end + "|" + code + "|" + name;
            if (!unique.add(key)) continue;
            ObjectNode row = result.addObject();
            row.put("course_code", code);
            row.put("course_name", name);
            row.put("day_of_week", day);
            row.put("start_time", start);
            row.put("end_time", end);
            row.put("section", section);
        }
        return result;
    }

    private ArrayNode normalizeTasks(JsonNode input, JsonNode classes, JsonNode plants) {
        ArrayNode result = mapper.createArrayNode();
        Set<String> plantIds = new HashSet<>();
        plants.forEach(plant -> plantIds.add(plant.path("id").asText()));
        if (!input.isArray()) return result;
        for (JsonNode item : input) {
            String plantId = item.path("plant_id").asText("");
            int day = item.path("day_of_week").asInt(0);
            String time = normalizeTime(item.path("start_time").asText(""));
            int duration = item.path("duration_minutes").asInt(20);
            String title = clean(item.path("title").asText(""), 180);
            String instructions = clean(item.path("instructions").asText(""), 1600);
            if (!plantIds.contains(plantId) || day < 1 || day > 7 || time.isBlank() || title.isBlank()) continue;
            duration = Math.max(5, Math.min(duration, 180));
            LocalTime start = LocalTime.parse(time);
            if (start.toSecondOfDay() / 60 + duration >= 24 * 60) continue;
            LocalTime end = start.plusMinutes(duration);
            if (overlapsClass(classes, day, start, end)) continue;
            ObjectNode task = result.addObject();
            task.put("plant_id", plantId);
            task.put("title", title);
            task.put("instructions", instructions);
            task.put("day_of_week", day);
            task.put("start_time", time);
            task.put("duration_minutes", duration);
        }
        return result;
    }

    private boolean overlapsClass(JsonNode classes, int day, LocalTime start, LocalTime end) {
        for (JsonNode event : classes) {
            if (event.path("day_of_week").asInt() != day) continue;
            String classStart = normalizeTime(event.path("start_time").asText(""));
            String classEnd = normalizeTime(event.path("end_time").asText(""));
            if (classStart.isBlank() || classEnd.isBlank()) continue;
            if (start.isBefore(LocalTime.parse(classEnd)) && end.isAfter(LocalTime.parse(classStart))) return true;
        }
        return false;
    }

    private String normalizeTime(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('.', ' ');
        try {
            return LocalTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ignored) {
            try {
                DateTimeFormatter format = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
                String normalized = value.replace("a m", "AM").replace("p m", "PM").replace("am", "AM").replace("pm", "PM").trim();
                return LocalTime.parse(normalized, format).format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception ignoredAgain) {
                return "";
            }
        }
    }

    private String clean(String value, int limit) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), limit));
    }

    private String truncate(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }

    private BufferedImage resizeIfNeeded(BufferedImage source) {
        int max = Math.max(source.getWidth(), source.getHeight());
        if (max <= MAX_IMAGE_EDGE) return source;
        double scale = (double) MAX_IMAGE_EDGE / max;
        BufferedImage resized = new BufferedImage((int) (source.getWidth() * scale), (int) (source.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        graphics.drawImage(source, 0, 0, resized.getWidth(), resized.getHeight(), null);
        graphics.dispose();
        source.flush();
        return resized;
    }
}
