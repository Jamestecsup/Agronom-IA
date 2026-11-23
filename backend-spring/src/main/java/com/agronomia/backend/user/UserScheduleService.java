package com.agronomia.backend.user;

import com.agronomia.backend.schedule.ScheduleAiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class UserScheduleService {
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ScheduleAiService ai;

    public UserScheduleService(JdbcTemplate jdbc, ObjectMapper json, ScheduleAiService ai) {
        this.jdbc = jdbc;
        this.json = json;
        this.ai = ai;
    }

    public Map<String, Object> weekly(long userId) {
        List<Map<String, Object>> schedules = jdbc.query("SELECT classes FROM horarios_semanales WHERE user_id=?",
                (rs, i) -> Map.of("classes", readList(rs.getString("classes"))), userId);
        List<Map<String, Object>> pending = jdbc.query(
                "SELECT id,parsed_classes FROM importaciones_horario_pendientes WHERE user_id=? ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("classes", readList(rs.getString("parsed_classes")));
                    return m;
                }, userId);
        List<Map<String, Object>> care = jdbc.query(
                "SELECT t.id,t.plant_id,t.title,t.instructions,t.day_of_week,t.start_time,t.duration_minutes,COALESCE(p.common_name,p.scientific_name) plant_name " +
                        "FROM tareas_cuidado t JOIN identificaciones_planta p ON p.id=t.plant_id WHERE t.user_id=? ORDER BY t.day_of_week,t.start_time",
                (rs, i) -> careTaskRow(rs), userId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("classes", schedules.isEmpty() ? List.of() : schedules.get(0).get("classes"));
        resp.put("care_tasks", care);
        resp.put("pending_import", pending.isEmpty() ? null : pending.get(0));
        return resp;
    }

    @Transactional
    public Map<String, Object> importPdf(long userId, MultipartFile pdf) {
        if (pdf == null || pdf.isEmpty()) throw new UserApiException(HttpStatus.BAD_REQUEST, "Selecciona el PDF de tu horario.");
        JsonNode parsed = ai.parseWeeklySchedule(pdf);
        List<Map<String, Object>> classes = normalizeClasses(parsed.path("classes"));
        jdbc.update("DELETE FROM importaciones_horario_pendientes WHERE user_id=?", userId);
        String id = UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO importaciones_horario_pendientes (id,user_id,source_pdf,parsed_classes,created_at) VALUES (?,?,?,?,?)",
                id, userId, "pending/" + id + ".pdf", writeJson(classes), Timestamp.valueOf(LocalDateTime.now()));
        return Map.of("success", true, "import_id", id, "classes", classes);
    }

    private Object toDbId(String id) {
        if (id == null) return null;
        String hex = id.replace("-", "");
        return hex.length() == 32 ? hex : id;
    }

    public Map<String, Object> pendingImport(long userId, String importId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT id,parsed_classes FROM importaciones_horario_pendientes WHERE id=? AND user_id=?",
                (rs, i) -> Map.of("id", rs.getString("id"), "classes", readList(rs.getString("parsed_classes"))),
                toDbId(importId), userId);
        if (rows.isEmpty()) throw new UserApiException(HttpStatus.NOT_FOUND, "La importacion ya no esta disponible.");
        Map<String, Object> r = new LinkedHashMap<>(rows.get(0));
        r.put("success", true);
        return r;
    }

    @Transactional
    public Map<String, Object> confirmImport(long userId, String importId, Object classesObj) {
        List<Map<String, Object>> classes = normalizeClasses(classesObj);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM importaciones_horario_pendientes WHERE id=? AND user_id=?",
                Integer.class, toDbId(importId), userId);
        if (exists == null || exists == 0) throw new UserApiException(HttpStatus.NOT_FOUND, "La importacion ya no esta disponible.");
        jdbc.update("INSERT INTO horarios_semanales (user_id,classes,updated_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE classes=VALUES(classes), updated_at=VALUES(updated_at)",
                userId, writeJson(classes), Timestamp.valueOf(LocalDateTime.now()));
        jdbc.update("DELETE FROM tareas_cuidado WHERE user_id=?", userId);
        jdbc.update("DELETE FROM importaciones_horario_pendientes WHERE id=? AND user_id=?", toDbId(importId), userId);
        return Map.of("success", true, "classes", classes, "pdf_removed", true);
    }

    @Transactional
    public Map<String, Object> cancelImport(long userId, String importId) {
        jdbc.update("DELETE FROM importaciones_horario_pendientes WHERE id=? AND user_id=?", toDbId(importId), userId);
        return Map.of("success", true);
    }

    @Transactional
    public Map<String, Object> draftCarePlan(long userId) {
        List<List<Map<String, Object>>> schedules = jdbc.query("SELECT classes FROM horarios_semanales WHERE user_id=?",
                (rs, i) -> readList(rs.getString("classes")), userId);
        if (schedules.isEmpty() || schedules.get(0).isEmpty()) {
            throw new UserApiException(HttpStatus.CONFLICT, "Primero importa y confirma tu horario semanal.");
        }
        List<Map<String, Object>> classes = schedules.get(0);
        List<Map<String, Object>> plants = jdbc.query(
                "SELECT id,scientific_name,common_name,result_json FROM identificaciones_planta WHERE user_id=? ORDER BY created_at ASC LIMIT 20",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("scientific_name", rs.getString("scientific_name"));
                    m.put("common_name", rs.getString("common_name"));
                    Map<String, Object> result = readMap(rs.getString("result_json"));
                    Object sections = result.get("sections");
                    StringBuilder care = new StringBuilder();
                    if (sections instanceof List<?> list) {
                        for (Object o : list) if (o instanceof Map<?, ?> s) care.append(s.get("title")).append(": ").append(s.get("body")).append("\n");
                    }
                    String careText = care.toString();
                    m.put("care_information", careText.length() > 7000 ? careText.substring(0, 7000) : careText);
                    return m;
                }, userId);
        if (plants.isEmpty()) throw new UserApiException(HttpStatus.CONFLICT, "Primero identifica al menos una planta.");
        JsonNode draft = ai.draftCarePlan(json.valueToTree(Map.of("classes", classes, "plants", plants)));
        List<Map<String, Object>> tasks = normalizeTasks(draft.path("tasks"), classes, plants);
        return Map.of("success", true, "tasks", tasks);
    }

    @Transactional
    public Map<String, Object> confirmCarePlan(long userId, Object tasksObj) {
        List<List<Map<String, Object>>> schedules = jdbc.query("SELECT classes FROM horarios_semanales WHERE user_id=?",
                (rs, i) -> readList(rs.getString("classes")), userId);
        if (schedules.isEmpty()) throw new UserApiException(HttpStatus.CONFLICT, "Primero confirma tu horario semanal.");
        List<Map<String, Object>> tasks = normalizeTasks(tasksObj, schedules.get(0), null);
        jdbc.update("DELETE FROM tareas_cuidado WHERE user_id=?", userId);
        for (Map<String, Object> t : tasks) {
            jdbc.update("INSERT INTO tareas_cuidado (id,user_id,plant_id,title,instructions,day_of_week,start_time,duration_minutes,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString().replace("-",""), userId, toDbId(String.valueOf(t.get("plant_id"))),
                    String.valueOf(t.get("title")), String.valueOf(t.getOrDefault("instructions", "")),
                    ((Number) t.get("day_of_week")).intValue(), LocalTime.parse(String.valueOf(t.get("start_time"))),
                    ((Number) t.get("duration_minutes")).intValue(), Timestamp.valueOf(LocalDateTime.now()));
        }
        Map<String, Object> weekly = weekly(userId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("care_tasks", weekly.get("care_tasks"));
        return resp;
    }

    @Transactional
    public Map<String, Object> rescheduleTask(long userId, String taskId, int day, String startTime) {
        List<List<Map<String, Object>>> schedules = jdbc.query("SELECT classes FROM horarios_semanales WHERE user_id=?",
                (rs, i) -> readList(rs.getString("classes")), userId);
        if (schedules.isEmpty()) throw new UserApiException(HttpStatus.CONFLICT, "Primero confirma tu horario semanal.");
        Object taskDbId = toDbId(taskId);
        List<Map<String, Object>> rows = jdbc.query("SELECT duration_minutes FROM tareas_cuidado WHERE id=? AND user_id=?",
                (rs, i) -> Map.of("duration_minutes", rs.getInt("duration_minutes")), taskDbId, userId);
        if (rows.isEmpty()) throw new UserApiException(HttpStatus.NOT_FOUND, "No se encontro ese cuidado.");
        int duration = ((Number) rows.get(0).get("duration_minutes")).intValue();
        LocalTime start = LocalTime.parse(startTime);
        if (hasClassConflict(schedules.get(0), day, start, duration) || hasTaskConflict(userId, taskId, day, start, duration)) {
            throw new UserApiException(HttpStatus.CONFLICT, "El nuevo horario se cruza con una clase u otro cuidado.");
        }
        jdbc.update("UPDATE tareas_cuidado SET day_of_week=?, start_time=? WHERE id=? AND user_id=?", day, start, taskDbId, userId);
        List<Map<String, Object>> task = jdbc.query(
                "SELECT t.id,t.plant_id,t.title,t.instructions,t.day_of_week,t.start_time,t.duration_minutes,COALESCE(p.common_name,p.scientific_name) plant_name " +
                        "FROM tareas_cuidado t JOIN identificaciones_planta p ON p.id=t.plant_id WHERE t.id=? AND t.user_id=?",
                (rs, i) -> careTaskRow(rs), taskDbId, userId);
        return Map.of("success", true, "care_task", task.get(0));
    }

    @Transactional
    public Map<String, Object> deleteTask(long userId, String taskId) {
        jdbc.update("DELETE FROM tareas_cuidado WHERE id=? AND user_id=?", toDbId(taskId), userId);
        return Map.of("success", true);
    }

    private boolean hasClassConflict(List<?> classes, int day, LocalTime start, int duration) {
        int s = start.toSecondOfDay() / 60, e = s + duration;
        for (Object o : classes) {
            if (!(o instanceof Map<?, ?> m)) continue;
            if (((Number) m.get("day_of_week")).intValue() != day) continue;
            int a = LocalTime.parse(String.valueOf(m.get("start_time"))).toSecondOfDay() / 60;
            int b = LocalTime.parse(String.valueOf(m.get("end_time"))).toSecondOfDay() / 60;
            if (s < b && e > a) return true;
        }
        return false;
    }

    private boolean hasTaskConflict(long userId, String taskId, int day, LocalTime start, int duration) {
        int s = start.toSecondOfDay() / 60, e = s + duration;
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT start_time,duration_minutes FROM tareas_cuidado WHERE user_id=? AND day_of_week=? AND id<>?",
                (rs, i) -> Map.of("start_time", rs.getString("start_time"), "duration_minutes", rs.getInt("duration_minutes")),
                userId, day, toDbId(taskId));
        for (Map<String, Object> r : rows) {
            int a = LocalTime.parse(String.valueOf(r.get("start_time")).substring(0, 5)).toSecondOfDay() / 60;
            int b = a + ((Number) r.get("duration_minutes")).intValue();
            if (s < b && e > a) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeClasses(Object input) {
        List<?> list;
        if (input instanceof JsonNode n) list = json.convertValue(n, List.class);
        else if (input instanceof List<?> l) list = l;
        else throw new UserApiException(HttpStatus.UNPROCESSABLE_ENTITY, "El horario enviado no es valido.");
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            Map<String, Object> m = (Map<String, Object>) raw;
            int day = toInt(m.get("day_of_week"));
            String start = normalizeTime(String.valueOf(m.getOrDefault("start_time", "")));
            String end = normalizeTime(String.valueOf(m.getOrDefault("end_time", "")));
            String code = str(m.getOrDefault("course_code", m.get("code")), 32);
            String name = str(m.getOrDefault("course_name", m.get("name")), 180);
            String section = str(m.get("section"), 80);
            if (day < 1 || day > 7 || start.isBlank() || end.isBlank() || name.isBlank()) continue;
            if (!LocalTime.parse(start).isBefore(LocalTime.parse(end))) continue;
            String key = day + "|" + start + "|" + end + "|" + code + "|" + name;
            if (!seen.add(key)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("course_code", code); row.put("course_name", name); row.put("day_of_week", day);
            row.put("start_time", start); row.put("end_time", end); row.put("section", section);
            out.add(row);
        }
        for (int i = 0; i < out.size(); i++) {
            for (int j = i + 1; j < out.size(); j++) {
                Map<String, Object> a = out.get(i), b = out.get(j);
                if (!a.get("day_of_week").equals(b.get("day_of_week"))) continue;
                if (String.valueOf(a.get("start_time")).compareTo(String.valueOf(b.get("end_time"))) < 0
                        && String.valueOf(a.get("end_time")).compareTo(String.valueOf(b.get("start_time"))) > 0) {
                    throw new UserApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Hay dos clases que se cruzan; corrige los bloques antes de confirmar.");
                }
            }
        }
        out.sort(Comparator.<Map<String, Object>, Integer>comparing(r -> ((Number) r.get("day_of_week")).intValue())
                .thenComparing(r -> String.valueOf(r.get("start_time"))));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeTasks(Object input, List<?> classes, List<Map<String, Object>> plants) {
        List<?> list;
        if (input instanceof JsonNode n) list = json.convertValue(n, List.class);
        else if (input instanceof List<?> l) list = l;
        else list = List.of();
        Set<String> plantIds = plants == null ? null : plants.stream().map(p -> String.valueOf(p.get("id"))).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            Map<String, Object> m = (Map<String, Object>) raw;
            String plantId = String.valueOf(m.get("plant_id"));
            int day = toInt(m.getOrDefault("day_of_week", 0));
            String startStr = normalizeTime(String.valueOf(m.getOrDefault("start_time", "")));
            int duration = Math.max(5, Math.min(toInt(m.getOrDefault("duration_minutes", 20)), 180));
            String title = str(m.get("title"), 180);
            String instructions = str(m.get("instructions"), 1600);
            if (title.isBlank() || day < 1 || day > 7 || startStr.isBlank()) continue;
            if (plantIds != null && !plantIds.contains(plantId)) continue;
            LocalTime start = LocalTime.parse(startStr);
            if (start.toSecondOfDay() / 60 + duration >= 24 * 60) continue;
            if (hasClassConflict(classes, day, start, duration)) continue;
            boolean overlap = false;
            for (Map<String, Object> ex : out) {
                if (((Number) ex.get("day_of_week")).intValue() != day) continue;
                int a = LocalTime.parse(String.valueOf(ex.get("start_time"))).toSecondOfDay() / 60;
                int b = a + ((Number) ex.get("duration_minutes")).intValue();
                int s = start.toSecondOfDay() / 60, e = s + duration;
                if (s < b && e > a) { overlap = true; break; }
            }
            if (overlap) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plant_id", plantId);
            row.put("title", title);
            row.put("instructions", instructions);
            row.put("day_of_week", day);
            row.put("start_time", startStr);
            row.put("duration_minutes", duration);
            if (m.containsKey("plant_name")) row.put("plant_name", str(m.get("plant_name"), 180));
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> careTaskRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("plant_id", rs.getString("plant_id"));
        m.put("plant_name", rs.getString("plant_name"));
        m.put("title", rs.getString("title"));
        m.put("instructions", rs.getString("instructions"));
        m.put("day_of_week", rs.getInt("day_of_week"));
        String st = rs.getString("start_time");
        m.put("start_time", st == null ? "08:00" : st.substring(0, 5));
        m.put("duration_minutes", rs.getInt("duration_minutes"));
        return m;
    }

    private String normalizeTime(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        try { return LocalTime.parse(v.length() >= 5 ? v.substring(0, 5) : v).toString().substring(0, 5); }
        catch (Exception e) { return ""; }
    }
    private String str(Object o, int max) { String s = o == null ? "" : String.valueOf(o).trim(); return s.length() > max ? s.substring(0, max) : s; }
    private int toInt(Object o) { return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o)); }
    private String writeJson(Object o) { try { return json.writeValueAsString(o); } catch (Exception e) { return "[]"; } }
    private List<Map<String, Object>> readList(String s) { try { return json.readValue(s, LIST); } catch (Exception e) { return new ArrayList<>(); } }
    private Map<String, Object> readMap(String s) { try { return json.readValue(s, MAP); } catch (Exception e) { return new LinkedHashMap<>(); } }
}
