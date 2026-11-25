package com.agronomia.backend.user;

import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.schedule.ScheduleAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/agronomia-api/api/schedule")
public class UserScheduleController {
    private static final String AUTHORIZATION = "Authorization";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private final UserAccounts accounts;
    private final UserPlants plants;
    private final UserFiles files;
    private final ScheduleAiService scheduleAi;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public UserScheduleController(UserAccounts accounts, UserPlants plants, UserFiles files,
                                  ScheduleAiService scheduleAi, JdbcTemplate jdbc, ObjectMapper json,
                                  PlatformTransactionManager transactions) {
        this.accounts = accounts;
        this.plants = plants;
        this.files = files;
        this.scheduleAi = scheduleAi;
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
    }

    @GetMapping
    public ObjectNode schedule(@RequestHeader(name = AUTHORIZATION, required = false) String token) {
        long user = accounts.requireUser(token);
        Pending pending = pending(user, null);
        if (pending != null && pending.expired) pending = null;
        ObjectNode out = success();
        JsonNode schedule = classes(user);
        out.set("classes", schedule == null ? json.createArrayNode() : schedule);
        out.set("care_tasks", taskList(user));
        if (pending == null) out.putNull("pending_import");
        else {
            ObjectNode part = out.putObject("pending_import").put("id", UserPlants.publicId(pending.id));
            part.set("classes", pending.classes);
        }
        return out;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectNode upload(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                             @RequestParam(required = false) MultipartFile pdf) {
        long user = accounts.requireUser(token);
        if (pdf == null || pdf.isEmpty()) throw bad("Selecciona el PDF de tu horario.");
        if (pdf.getSize() > 15 * 1024 * 1024L) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "El PDF supera el límite de 15 MB.");
        try (var input = pdf.getInputStream()) {
            if (!java.util.Arrays.equals(input.readNBytes(5), "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw invalid("El archivo no parece ser un PDF válido.");
            }
        } catch (IOException error) {
            throw invalid("No se pudo leer el archivo PDF.");
        }
        ArrayNode parsed;
        try {
            parsed = normalize(scheduleAi.parseWeeklySchedule(pdf, user).path("classes"));
        } catch (ResponseStatusException error) {
            throw new ApiException(HttpStatus.valueOf(error.getStatusCode().value()), error.getReason());
        }
        String id = UserPlants.newId();
        String path = "schedules/pending/" + id + ".pdf";
        var dest = files.resolve(path);
        List<String> previous;
        try {
            Files.createDirectories(dest.getParent());
            pdf.transferTo(dest);
            previous = locked(user, () -> {
                List<String> old = jdbc.queryForList("SELECT source_pdf FROM importaciones_horario_pendientes WHERE user_id=?", String.class, user);
                jdbc.update("DELETE FROM importaciones_horario_pendientes WHERE user_id=?", user);
                jdbc.update("""
                        INSERT INTO importaciones_horario_pendientes(id,user_id,source_pdf,parsed_classes,created_at)
                        VALUES(?,?,?,?,UTC_TIMESTAMP(6))
                        """, id, user, path, parsed.toString());
                return old;
            });
        } catch (IOException | RuntimeException failure) {
            deleteFile(path);
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el PDF temporal.");
        }
        previous.forEach(this::deleteFile);
        return withClasses(success().put("import_id", UserPlants.publicId(id)), parsed);
    }

    @GetMapping("/import/{publicId}")
    public ObjectNode importInfo(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                                 @PathVariable String publicId) {
        Pending p = current(accounts.requireUser(token), publicId);
        return withClasses(success().put("import_id", UserPlants.publicId(p.id)), p.classes);
    }

    @DeleteMapping("/import/{publicId}")
    public ObjectNode cancel(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                             @PathVariable String publicId) {
        long user = accounts.requireUser(token);
        Pending p = current(user, publicId);
        dropPending(user, p);
        return success();
    }

    @PostMapping("/import/{publicId}")
    public ObjectNode confirm(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                              @PathVariable String publicId, @RequestBody JsonNode body) {
        long user = accounts.requireUser(token);
        Pending p = current(user, publicId);
        ArrayNode normalized = normalize(body.path("classes"));
        locked(user, () -> {
            if (jdbc.update("DELETE FROM importaciones_horario_pendientes WHERE id=? AND user_id=?", p.id, user) != 1) {
                throw missingImport();
            }
            jdbc.update("""
                    INSERT INTO horarios_semanales(user_id,classes,updated_at) VALUES(?,?,UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE classes=VALUES(classes),updated_at=UTC_TIMESTAMP(6)
                    """, user, normalized.toString());
            jdbc.update("DELETE FROM tareas_cuidado WHERE user_id=?", user);
            return null;
        });
        deleteFile(p.pdf);
        return withClasses(success().put("pdf_removed", true), normalized);
    }

    @PostMapping("/care-plan/draft")
    public ObjectNode draft(@RequestHeader(name = AUTHORIZATION, required = false) String token) {
        long user = accounts.requireUser(token);
        JsonNode schedule = requireClasses(user);
        List<UserPlants.StoredPlant> available = new ArrayList<>(plants.plants(user));
        java.util.Collections.reverse(available);
        if (available.isEmpty()) throw conflict("Primero identifica al menos una planta.");
        var names = new HashMap<String, UserPlants.StoredPlant>();
        ArrayNode payload = json.createArrayNode();
        available.stream().limit(20).forEach(plant -> {
            names.put(plant.publicId(), plant);
            StringBuilder care = new StringBuilder();
            plant.card().path("sections").forEach(section -> care.append(section.path("title").asText())
                    .append(": ").append(section.path("body").asText()).append('\n'));
            payload.addObject().put("id", plant.publicId()).put("scientific_name", plant.scientific())
                    .put("common_name", plant.common()).put("care_information", limit(care.toString(), 7000));
        });
        ObjectNode input = json.createObjectNode();
        input.set("classes", schedule);
        input.set("plants", payload);
        JsonNode proposed;
        try {
            proposed = scheduleAi.draftCarePlan(input, user).path("tasks");
        } catch (ResponseStatusException error) {
            throw new ApiException(HttpStatus.valueOf(error.getStatusCode().value()), error.getReason());
        }
        ArrayNode result = json.createArrayNode();
        List<Task> accepted = new ArrayList<>();
        for (JsonNode item : proposed) {
            UserPlants.StoredPlant plant = names.get(item.path("plant_id").asText());
            if (plant == null) continue;
            try {
                Task task = fromNode(item, plant.id(), name(plant));
                if (fits(task, schedule, accepted)) {
                    accepted.add(task);
                    result.add(taskNode(task, false));
                }
            } catch (ApiException ignored) { /* Se descartan propuestas inválidas. */ }
        }
        ObjectNode response = success();
        response.set("tasks", result);
        return response;
    }

    @PostMapping("/care-plan/confirm")
    public ObjectNode confirmCare(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                                  @RequestBody JsonNode body) {
        long user = accounts.requireUser(token);
        JsonNode input = body.path("tasks");
        if (!input.isArray() || input.size() > 60) throw invalid("El plan debe tener como máximo 60 acciones semanales.");
        return locked(user, () -> {
            JsonNode schedule = requireClasses(user);
            List<Task> list = new ArrayList<>();
            Map<String, UserPlants.StoredPlant> owned = new HashMap<>();
            plants.plants(user).forEach(plant -> owned.put(plant.publicId(), plant));
            for (JsonNode row : input) {
                var plant = owned.get(row.path("plant_id").asText());
                if (plant == null) throw invalid("Revisa la planta, el día y la duración de cada cuidado.");
                var task = fromNode(row, plant.id(), name(plant));
                if (!fits(task, schedule, list)) throw invalid("Revisa el máximo por planta y los cruces de horario.");
                list.add(task);
            }
            jdbc.update("DELETE FROM tareas_cuidado WHERE user_id=?", user);
            for (Task task : list) {
                jdbc.update("""
                        INSERT INTO tareas_cuidado(id,user_id,plant_id,title,instructions,day_of_week,start_time,duration_minutes,created_at)
                        VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                        """, task.id, user, task.plant, task.title, task.instructions, task.day, task.start, task.duration);
            }
            ObjectNode result = success();
            result.set("care_tasks", taskList(user));
            return result;
        });
    }

    @PatchMapping("/care-plan/tasks/{publicId}")
    public ObjectNode reschedule(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                                 @PathVariable String publicId, @RequestBody JsonNode body) {
        long user = accounts.requireUser(token);
        String id = UserPlants.databaseId(publicId);
        return locked(user, () -> {
            List<Task> all = tasks(user);
            Task old = all.stream().filter(item -> item.id.equals(id)).findFirst().orElseThrow(UserScheduleController::missingTask);
            int day = body.path("day_of_week").asInt();
            LocalTime start = time(body.path("start_time").asText());
            Task edited = new Task(id, old.plant, old.plantName, old.title, old.instructions, day, start, old.duration);
            if (minute(start) + old.duration >= 1440) throw invalid("El cuidado no puede extenderse al día siguiente.");
            if (!fits(edited, requireClasses(user), all.stream().filter(task -> !task.id.equals(id)).toList())) {
                throw conflict("El nuevo horario se cruza con una clase u otro cuidado.");
            }
            jdbc.update("UPDATE tareas_cuidado SET day_of_week=?,start_time=? WHERE id=? AND user_id=?", day, start, id, user);
            ObjectNode result = success();
            result.set("care_task", taskNode(edited, true));
            return result;
        });
    }

    @DeleteMapping("/care-plan/tasks/{publicId}")
    public ObjectNode deleteTask(@RequestHeader(name = AUTHORIZATION, required = false) String token,
                                 @PathVariable String publicId) {
        long user = accounts.requireUser(token);
        if (jdbc.update("DELETE FROM tareas_cuidado WHERE id=? AND user_id=?", UserPlants.databaseId(publicId), user) == 0) {
            throw missingTask();
        }
        return success();
    }

    private Pending pending(long user, String id) {
        String sql = """
                SELECT id,source_pdf,parsed_classes,created_at < UTC_TIMESTAMP(6)-INTERVAL 48 HOUR expired
                FROM importaciones_horario_pendientes WHERE user_id=?
                """;
        List<Pending> rows = jdbc.query(sql + (id == null ? " ORDER BY created_at DESC LIMIT 1" : " AND id=?"),
                (row, index) -> new Pending(row.getString(1), row.getString(2), plants.decode(row.getString(3)), row.getBoolean(4)),
                id == null ? new Object[]{user} : new Object[]{user, id});
        return rows.stream().findFirst().orElse(null);
    }

    private Pending current(long user, String publicId) {
        Pending value = pending(user, UserPlants.databaseId(publicId));
        if (value == null) throw missingImport();
        if (value.expired) {
            dropPending(user, value);
            throw error(HttpStatus.GONE, "La revisión del PDF venció. Vuelve a subir tu horario.");
        }
        return value;
    }

    private void dropPending(long user, Pending value) {
        if (jdbc.update("DELETE FROM importaciones_horario_pendientes WHERE id=? AND user_id=?", value.id, user) == 1) {
            deleteFile(value.pdf);
        }
    }

    private void deleteFile(String path) {
        if (!path.replace('\\','/').startsWith("schedules/pending/")) return;
        try {
            Files.deleteIfExists(files.resolve(path));
        } catch (IOException | ApiException error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo retirar un PDF temporal.");
        }
    }

    private JsonNode classes(long user) {
        return jdbc.query("SELECT classes FROM horarios_semanales WHERE user_id=?",
                (row, index) -> plants.decode(row.getString(1)), user).stream().findFirst().orElse(null);
    }

    private JsonNode requireClasses(long user) {
        JsonNode schedule = classes(user);
        if (schedule == null) throw conflict("Primero importa y confirma tu horario semanal.");
        return schedule;
    }

    private List<Task> tasks(long user) {
        return jdbc.query("""
                SELECT t.*,COALESCE(NULLIF(p.common_name,''),p.scientific_name) plant_name
                FROM tareas_cuidado t JOIN identificaciones_planta p ON t.plant_id=p.id
                WHERE t.user_id=? ORDER BY t.day_of_week,t.start_time
                """, (row, index) -> new Task(row.getString("id"), row.getString("plant_id"), row.getString("plant_name"),
                row.getString("title"), row.getString("instructions"), row.getInt("day_of_week"),
                row.getObject("start_time", LocalTime.class), row.getInt("duration_minutes")), user);
    }

    private ArrayNode taskList(long user) {
        ArrayNode out = json.createArrayNode();
        tasks(user).forEach(task -> out.add(taskNode(task, true)));
        return out;
    }

    private ObjectNode taskNode(Task task, boolean withId) {
        ObjectNode node = json.createObjectNode().put("plant_id", UserPlants.publicId(task.plant))
                .put("plant_name", task.plantName).put("title", task.title).put("instructions", task.instructions)
                .put("day_of_week", task.day).put("start_time", HOUR.format(task.start)).put("duration_minutes", task.duration);
        if (withId) node.put("id", UserPlants.publicId(task.id));
        return node;
    }

    private ArrayNode normalize(JsonNode input) {
        if (!input.isArray() || input.isEmpty() || input.size() > 100) throw invalid("El horario debe tener entre 1 y 100 bloques.");
        var rows = new ArrayList<ObjectNode>();
        for (JsonNode item : input) {
            int day = item.path("day_of_week").asInt();
            LocalTime start = time(item.path("start_time").asText());
            LocalTime end = time(item.path("end_time").asText());
            String course = limit(item.path("course_name").asText(item.path("name").asText("")).strip(), 180);
            if (course.isEmpty() || day < 1 || day > 7 || !start.isBefore(end)) {
                throw invalid("Cada clase necesita un nombre, un día y un rango horario válido.");
            }
            for (ObjectNode existing : rows) {
                if (day == existing.path("day_of_week").asInt()
                        && overlap(minute(start), minute(end), minute(time(existing.path("start_time").asText())),
                        minute(time(existing.path("end_time").asText())))) throw invalid("Hay dos clases que se cruzan.");
            }
            rows.add(json.createObjectNode().put("day_of_week", day).put("course_name", course)
                    .put("course_code", limit(item.path("course_code").asText(item.path("code").asText("")).strip(), 32))
                    .put("start_time", HOUR.format(start)).put("end_time", HOUR.format(end))
                    .put("section", limit(item.path("section").asText().strip(), 80)));
        }
        rows.sort(Comparator.comparingInt((ObjectNode row) -> row.path("day_of_week").asInt())
                .thenComparing(row -> row.path("start_time").asText()));
        return json.createArrayNode().addAll(rows);
    }

    private Task fromNode(JsonNode item, String plant, String plantName) {
        String title = limit(item.path("title").asText().strip(), 180);
        int duration = item.path("duration_minutes").asInt(20);
        if (title.isBlank() || duration < 5 || duration > 180) throw invalid("Revisa el título y la duración de cada cuidado.");
        return new Task(UserPlants.newId(), plant, plantName, title,
                limit(item.path("instructions").asText().strip(), 1600), item.path("day_of_week").asInt(),
                time(item.path("start_time").asText()), duration);
    }

    private static boolean fits(Task task, JsonNode classes, List<Task> existing) {
        int start = minute(task.start), end = start + task.duration;
        if (task.day < 1 || task.day > 7 || end >= 1440
                || existing.stream().filter(row -> row.plant.equals(task.plant)).count() >= 3) return false;
        for (JsonNode block : classes) {
            if (block.path("day_of_week").asInt() == task.day
                    && overlap(start, end, minute(time(block.path("start_time").asText())),
                    minute(time(block.path("end_time").asText())))) return false;
        }
        return existing.stream().noneMatch(row -> row.day == task.day
                && overlap(start, end, minute(row.start), minute(row.start) + row.duration));
    }

    private <T> T locked(long user, Supplier<T> action) {
        return tx.execute(state -> {
            jdbc.queryForObject("SELECT id FROM auth_user WHERE id=? FOR UPDATE", Long.class, user);
            return action.get();
        });
    }

    private ObjectNode success() { return json.createObjectNode().put("success", true); }

    private static ObjectNode withClasses(ObjectNode output, JsonNode classes) {
        output.set("classes", classes);
        return output;
    }

    private static String name(UserPlants.StoredPlant plant) { return plant.common().isBlank() ? plant.scientific() : plant.common(); }
    private static int minute(LocalTime time) { return time.getHour() * 60 + time.getMinute(); }
    private static boolean overlap(int start, int end, int otherStart, int otherEnd) { return start < otherEnd && end > otherStart; }
    private static LocalTime time(String value) {
        try { return LocalTime.parse(value).withSecond(0).withNano(0); }
        catch (java.time.format.DateTimeParseException error) { throw invalid("Revisa los días y las horas del horario."); }
    }
    private static String limit(String text, int max) { return text.length() <= max ? text : text.substring(0, max); }
    private static ApiException missingTask() { return error(HttpStatus.NOT_FOUND, "No se encontró ese cuidado."); }
    private static ApiException missingImport() { return error(HttpStatus.NOT_FOUND, "La importación ya no está disponible."); }
    private static ApiException bad(String text) { return error(HttpStatus.BAD_REQUEST, text); }
    private static ApiException invalid(String text) { return error(HttpStatus.UNPROCESSABLE_ENTITY, text); }
    private static ApiException conflict(String text) { return error(HttpStatus.CONFLICT, text); }
    private static ApiException error(HttpStatus status, String text) { return new ApiException(status, text); }

    private record Pending(String id, String pdf, JsonNode classes, boolean expired) {}
    private record Task(String id, String plant, String plantName, String title, String instructions, int day,
                        LocalTime start, int duration) {}
}
