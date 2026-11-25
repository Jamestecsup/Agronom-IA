package com.agronomia.backend.user;

import com.agronomia.backend.schedule.ScheduleAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserScheduleResponseContractTest {
    @Autowired MockMvc mockMvc;
    @MockBean UserTokenService tokens;
    @MockBean UserScheduleRepository repository;
    @MockBean ScheduleAiService ai;

    @Test
    void weeklyScheduleKeepsSerializedMapContractAfterTypedConversion() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(repository.findWeeklySchedule(7L)).thenReturn(List.of(Map.of("classes", List.of(Map.of(
                "course_code", "BIO-1", "course_name", "Botánica", "day_of_week", 1,
                "start_time", "08:00", "end_time", "09:00", "section", "A")))));
        when(repository.findLatestPendingImport(7L)).thenReturn(List.of());
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "task-1");
        task.put("plant_id", "plant-1");
        task.put("plant_name", null);
        task.put("title", "Regar");
        task.put("instructions", "cada semana");
        task.put("day_of_week", 2);
        task.put("start_time", "10:30");
        task.put("duration_minutes", 15);
        when(repository.findCareTasks(7L)).thenReturn(List.of(task));

        mockMvc.perform(get("/agronomia-api/api/schedule").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"classes":[{"course_code":"BIO-1","course_name":"Botánica","day_of_week":1,"start_time":"08:00","end_time":"09:00","section":"A"}],"care_tasks":[{"id":"task-1","plant_id":"plant-1","title":"Regar","instructions":"cada semana","day_of_week":2,"start_time":"10:30","duration_minutes":15}]}
                        """));
    }

    @Test
    void weeklyScheduleKeepsOptionalPendingImportAndUnauthorizedError() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(repository.findWeeklySchedule(7L)).thenReturn(List.of(Map.of("classes", List.of())));
        when(repository.findLatestPendingImport(7L)).thenReturn(List.of(Map.of(
                "id", "import-1", "classes", List.of(Map.of("course_code", "MAT-2", "course_name", "Matemática",
                        "day_of_week", 3, "start_time", "09:00", "end_time", "10:00", "section", "B")))));
        when(repository.findCareTasks(7L)).thenReturn(List.of());

        mockMvc.perform(get("/agronomia-api/api/schedule").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"classes":[],"care_tasks":[],"pending_import":{"id":"import-1","classes":[{"course_code":"MAT-2","course_name":"Matemática","day_of_week":3,"start_time":"09:00","end_time":"10:00","section":"B"}]}}
                        """));

        mockMvc.perform(get("/agronomia-api/api/schedule"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"success":false,"error":"Inicia sesión para continuar."}
                        """));
    }
}
