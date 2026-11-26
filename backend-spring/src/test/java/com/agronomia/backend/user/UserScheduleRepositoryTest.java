package com.agronomia.backend.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserScheduleRepositoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserScheduleRepository repository;

    @BeforeEach
    void createScheduleTablesInTestDatabase() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS pending_schedule_imports (id CHAR(32) PRIMARY KEY, user_id BIGINT NOT NULL, source_pdf VARCHAR(255) NOT NULL, parsed_classes LONGTEXT NOT NULL, created_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS weekly_schedules (user_id BIGINT PRIMARY KEY, classes LONGTEXT NOT NULL, updated_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS care_plan_tasks (id CHAR(32) PRIMARY KEY, user_id BIGINT NOT NULL, plant_id CHAR(36) NOT NULL, title VARCHAR(180) NOT NULL, instructions LONGTEXT NOT NULL, day_of_week INT NOT NULL, start_time TIME NOT NULL, duration_minutes INT NOT NULL, created_at TIMESTAMP NOT NULL)");
    }

    @Test
    void pendingImportIsMappedScopedAndReplaceablePerOwner() {
        Timestamp now = Timestamp.valueOf("2026-01-01 10:00:00");
        repository.insertPendingImport("import-owner-7", 7, "pending/a.pdf", "[{\"course_name\":\"Botany\"}]", now);
        repository.insertPendingImport("import-owner-8", 8, "pending/b.pdf", "[]", Timestamp.valueOf("2026-01-01 11:00:00"));

        assertThat(repository.findPendingImport("import-owner-7", 7)).containsExactly(
                Map.of("id", "import-owner-7", "classes", List.of(Map.of("course_name", "Botany"))));
        assertThat(repository.findPendingImport("import-owner-7", 8)).isEmpty();
        assertThat(repository.pendingImportExists("import-owner-7", 7)).isTrue();
        repository.deletePendingImport("import-owner-7", 7);
        assertThat(repository.pendingImportExists("import-owner-7", 7)).isFalse();
    }

    @Test
    void weeklyScheduleAndCareTaskWritesMapBackToContractRows() {
        long isolatedUserId = 987654L;
        repository.upsertWeeklySchedule(isolatedUserId, "[{\"course_name\":\"Botany\"}]", Timestamp.valueOf("2026-01-01 10:00:00"));
        assertThat(repository.findWeeklyClasses(isolatedUserId)).containsExactly(List.of(Map.of("course_name", "Botany")));
        jdbc.update("INSERT INTO plant_identifications(id,user_id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                "plant-1", isolatedUserId, "batch-1", "Rosa canina", "Dog rose", "Rosaceae", .9,
                "{\"sections\":[{\"title\":\"Riego\",\"body\":\"Semanal\"}]}", Timestamp.valueOf("2026-01-01 09:00:00"));
        repository.insertCareTask("task-1", isolatedUserId, "plant-1", "Regar", "", 1, LocalTime.of(8, 30), 20,
                Timestamp.valueOf("2026-01-01 10:00:00"));

        assertThat(repository.findPlantsForCarePlan(isolatedUserId)).containsExactly(Map.of(
                "id", "plant-1", "scientific_name", "Rosa canina", "common_name", "Dog rose", "care_information", "Riego: Semanal\n"));
        assertThat(repository.findCareTasks(isolatedUserId)).containsExactly(Map.of(
                "id", "task-1", "plant_id", "plant-1", "plant_name", "Dog rose", "title", "Regar", "instructions", "",
                "day_of_week", 1, "start_time", "08:30", "duration_minutes", 20));
        assertThat(repository.findCareTaskDuration("task-1", isolatedUserId)).isEqualTo(20);
        assertThat(repository.findCareTaskDuration("task-1", 8)).isNull();
        repository.updateCareTaskTime(isolatedUserId, "task-1", 2, LocalTime.of(9, 0));
        List<Map<String, Object>> otherTasks = repository.findOtherCareTasks(isolatedUserId, 2, "task-2");
        assertThat(otherTasks).singleElement().satisfies(task -> {
            assertThat(String.valueOf(task.get("start_time"))).startsWith("09:00:00");
            assertThat(task).containsEntry("duration_minutes", 20);
        });
        repository.deleteCareTask(isolatedUserId, "task-1");
        assertThat(repository.findCareTasks(isolatedUserId)).isEmpty();
    }
}
