package com.agronomia.backend.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.*;

@Repository
public class UserScheduleRepository {
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public UserScheduleRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<List<Map<String, Object>>> findWeeklyClasses(long userId) {
        return jdbc.query("SELECT classes FROM weekly_schedules WHERE user_id=?",
                (rs, i) -> readList(rs.getString("classes")), userId);
    }

    public List<Map<String, Object>> findWeeklySchedule(long userId) {
        return jdbc.query("SELECT classes FROM weekly_schedules WHERE user_id=?",
                (rs, i) -> Map.of("classes", readList(rs.getString("classes"))), userId);
    }

    public List<Map<String, Object>> findLatestPendingImport(long userId) {
        return jdbc.query("SELECT id,parsed_classes FROM pending_schedule_imports WHERE user_id=? ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("classes", readList(rs.getString("parsed_classes")));
                    return row;
                }, userId);
    }

    public List<Map<String, Object>> findCareTasks(long userId) {
        return jdbc.query("SELECT t.id,t.plant_id,t.title,t.instructions,t.day_of_week,t.start_time,t.duration_minutes,COALESCE(p.common_name,p.scientific_name) plant_name " +
                        "FROM care_plan_tasks t JOIN plant_identifications p ON p.id=t.plant_id WHERE t.user_id=? ORDER BY t.day_of_week,t.start_time",
                (rs, i) -> careTaskRow(rs), userId);
    }

    public void deletePendingImports(long userId) {
        jdbc.update("DELETE FROM pending_schedule_imports WHERE user_id=?", userId);
    }

    public void insertPendingImport(String id, long userId, String sourcePdf, String classesJson, Timestamp createdAt) {
        jdbc.update("INSERT INTO pending_schedule_imports (id,user_id,source_pdf,parsed_classes,created_at) VALUES (?,?,?,?,?)",
                id, userId, sourcePdf, classesJson, createdAt);
    }

    public List<Map<String, Object>> findPendingImport(String id, long userId) {
        return jdbc.query("SELECT id,parsed_classes FROM pending_schedule_imports WHERE id=? AND user_id=?",
                (rs, i) -> Map.of("id", rs.getString("id"), "classes", readList(rs.getString("parsed_classes"))), id, userId);
    }

    public boolean pendingImportExists(String id, long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pending_schedule_imports WHERE id=? AND user_id=?",
                Integer.class, id, userId);
        return count != null && count > 0;
    }

    public void upsertWeeklySchedule(long userId, String classesJson, Timestamp updatedAt) {
        jdbc.update("INSERT INTO weekly_schedules (user_id,classes,updated_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE classes=VALUES(classes), updated_at=VALUES(updated_at)",
                userId, classesJson, updatedAt);
    }

    public void deleteCareTasks(long userId) {
        jdbc.update("DELETE FROM care_plan_tasks WHERE user_id=?", userId);
    }

    public void deletePendingImport(String id, long userId) {
        jdbc.update("DELETE FROM pending_schedule_imports WHERE id=? AND user_id=?", id, userId);
    }

    public List<Map<String, Object>> findPlantsForCarePlan(long userId) {
        return jdbc.query("SELECT id,scientific_name,common_name,result_json FROM plant_identifications WHERE user_id=? ORDER BY created_at ASC LIMIT 20",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("scientific_name", rs.getString("scientific_name"));
                    row.put("common_name", rs.getString("common_name"));
                    Map<String, Object> result = readMap(rs.getString("result_json"));
                    Object sections = result.get("sections");
                    StringBuilder care = new StringBuilder();
                    if (sections instanceof List<?> list) {
                        for (Object item : list) if (item instanceof Map<?, ?> section)
                            care.append(section.get("title")).append(": ").append(section.get("body")).append("\n");
                    }
                    String careText = care.toString();
                    row.put("care_information", careText.length() > 7000 ? careText.substring(0, 7000) : careText);
                    return row;
                }, userId);
    }

    public void insertCareTask(String id, long userId, String plantId, String title, String instructions,
                               int day, LocalTime start, int duration, Timestamp createdAt) {
        jdbc.update("INSERT INTO care_plan_tasks (id,user_id,plant_id,title,instructions,day_of_week,start_time,duration_minutes,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id, userId, plantId, title, instructions, day, start, duration, createdAt);
    }

    public Integer findCareTaskDuration(String taskId, long userId) {
        List<Integer> rows = jdbc.query("SELECT duration_minutes FROM care_plan_tasks WHERE id=? AND user_id=?",
                (rs, i) -> rs.getInt("duration_minutes"), taskId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> findOtherCareTasks(long userId, int day, String taskId) {
        return jdbc.query("SELECT start_time,duration_minutes FROM care_plan_tasks WHERE user_id=? AND day_of_week=? AND id<>?",
                (rs, i) -> Map.of("start_time", rs.getString("start_time"), "duration_minutes", rs.getInt("duration_minutes")),
                userId, day, taskId);
    }

    public void updateCareTaskTime(long userId, String taskId, int day, LocalTime start) {
        jdbc.update("UPDATE care_plan_tasks SET day_of_week=?, start_time=? WHERE id=? AND user_id=?", day, start, taskId, userId);
    }

    public List<Map<String, Object>> findCareTask(long userId, String taskId) {
        return jdbc.query("SELECT t.id,t.plant_id,t.title,t.instructions,t.day_of_week,t.start_time,t.duration_minutes,COALESCE(p.common_name,p.scientific_name) plant_name " +
                        "FROM care_plan_tasks t JOIN plant_identifications p ON p.id=t.plant_id WHERE t.id=? AND t.user_id=?",
                (rs, i) -> careTaskRow(rs), taskId, userId);
    }

    public void deleteCareTask(long userId, String taskId) {
        jdbc.update("DELETE FROM care_plan_tasks WHERE id=? AND user_id=?", taskId, userId);
    }

    private Map<String, Object> careTaskRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("plant_id", rs.getString("plant_id"));
        row.put("plant_name", rs.getString("plant_name"));
        row.put("title", rs.getString("title"));
        row.put("instructions", rs.getString("instructions"));
        row.put("day_of_week", rs.getInt("day_of_week"));
        String start = rs.getString("start_time");
        row.put("start_time", start == null ? "08:00" : start.substring(0, 5));
        row.put("duration_minutes", rs.getInt("duration_minutes"));
        return row;
    }

    private List<Map<String, Object>> readList(String value) {
        try { return json.readValue(value, LIST); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private Map<String, Object> readMap(String value) {
        try { return json.readValue(value, MAP); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }
}
