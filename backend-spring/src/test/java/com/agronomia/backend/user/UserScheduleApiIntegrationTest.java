package com.agronomia.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserScheduleApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockBean UserTokenService tokens;
    @MockBean UserScheduleService schedule;

    @Test
    void weeklyScheduleKeepsResponseShapeAndRequiresAuthentication() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(schedule.weeklyResponse(7L)).thenReturn(new UserScheduleDtos.WeeklyResponse(true,
                List.of(new UserScheduleDtos.WeeklyClass("BIO-1", "Botánica", 1, "08:00", "09:00", "A")),
                List.of(new UserScheduleDtos.CareTask("task-1", "plant-1", null, "Regar", "cada semana", 2, "10:30", 15)),
                null));

        mockMvc.perform(get("/agronomia-api/api/schedule").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"classes":[{"course_code":"BIO-1","course_name":"Botánica","day_of_week":1,"start_time":"08:00","end_time":"09:00","section":"A"}],"care_tasks":[{"id":"task-1","plant_id":"plant-1","title":"Regar","instructions":"cada semana","day_of_week":2,"start_time":"10:30","duration_minutes":15}]}
                        """));
        mockMvc.perform(get("/agronomia-api/api/schedule"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"success":false,"error":"Inicia sesión para continuar."}
                        """));
    }

    @Test
    void weeklyScheduleSerializesOptionalPendingImportWithTheAndroidFieldNames() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(schedule.weeklyResponse(7L)).thenReturn(new UserScheduleDtos.WeeklyResponse(true, List.of(), List.of(),
                new UserScheduleDtos.PendingImport("import-1", List.of(
                        new UserScheduleDtos.WeeklyClass("MAT-2", "Matemática", 3, "09:00", "10:00", "B")))));

        mockMvc.perform(get("/agronomia-api/api/schedule").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"classes":[],"care_tasks":[],"pending_import":{"id":"import-1","classes":[{"course_code":"MAT-2","course_name":"Matemática","day_of_week":3,"start_time":"09:00","end_time":"10:00","section":"B"}]}}
                        """));
    }

    @Test
    void scheduleImportMultipartKeepsCreatedStatusAndResponseFields() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(schedule.importPdf(eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("success", true, "import_id", "import-1", "classes", List.of()));
        MockMultipartFile pdf = new MockMultipartFile("pdf", "schedule.pdf", "application/pdf", new byte[]{1, 2});

        mockMvc.perform(multipart("/agronomia-api/api/schedule/import").file(pdf)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.import_id").value("import-1")).andExpect(jsonPath("$.classes").isArray());
        verify(schedule).importPdf(eq(7L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pendingImportConfirmAndCancelKeepRoutesAndPayloadContract() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(schedule.pendingImport(7L, "import-1")).thenReturn(Map.of("success", true, "id", "import-1", "classes", List.of()));
        when(schedule.confirmImport(eq(7L), eq("import-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("success", true, "classes", List.of(), "pdf_removed", true));
        when(schedule.cancelImport(7L, "import-1")).thenReturn(Map.of("success", true));

        mockMvc.perform(get("/agronomia-api/api/schedule/import/import-1").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("import-1"));
        mockMvc.perform(post("/agronomia-api/api/schedule/import/import-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classes\":[]}").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pdf_removed").value(true));
        mockMvc.perform(delete("/agronomia-api/api/schedule/import/import-1").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(schedule).confirmImport(eq(7L), eq("import-1"), eq(List.of()));
    }

    @Test
    void carePlanTaskRoutesKeepMethodsParametersAndResponseShape() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(schedule.rescheduleTask(7L, "task-1", 2, "09:30"))
                .thenReturn(Map.of("success", true, "care_task", Map.of("id", "task-1", "start_time", "09:30")));
        when(schedule.deleteTask(7L, "task-1")).thenReturn(Map.of("success", true));

        mockMvc.perform(patch("/agronomia-api/api/schedule/care-plan/tasks/task-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"day_of_week\":2,\"start_time\":\"09:30\"}")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.care_task.start_time").value("09:30"));
        mockMvc.perform(delete("/agronomia-api/api/schedule/care-plan/tasks/task-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(schedule).rescheduleTask(7L, "task-1", 2, "09:30");
        verify(schedule).deleteTask(7L, "task-1");
    }
}
