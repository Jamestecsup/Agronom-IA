package com.agronomia.backend.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class UserScheduleDtos {
    private UserScheduleDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WeeklyResponse(
            boolean success,
            List<WeeklyClass> classes,
            @JsonProperty("care_tasks") List<CareTask> careTasks,
            @JsonProperty("pending_import") PendingImport pendingImport
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WeeklyClass(
            @JsonProperty("course_code") String courseCode,
            @JsonProperty("course_name") String courseName,
            @JsonProperty("day_of_week") Integer dayOfWeek,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            String section
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CareTask(
            String id,
            @JsonProperty("plant_id") String plantId,
            @JsonProperty("plant_name") String plantName,
            String title,
            String instructions,
            @JsonProperty("day_of_week") Integer dayOfWeek,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("duration_minutes") Integer durationMinutes
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendingImport(String id, List<WeeklyClass> classes) {}
}
