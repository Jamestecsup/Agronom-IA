package com.agronomia.backend.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Mapeos para validar el esquema compartido al iniciar Spring. Flyway controla el DDL del dominio. */
public final class SchemaEntities {
    private SchemaEntities() {
    }

    @Entity
    @Table(name = "auth_user")
    public static class AuthUser {
        @Id
        @Column(name = "id", nullable = false)
        private Integer id;

        @Column(name = "password", length = 128, nullable = false)
        private String password;

        @Column(name = "last_login", columnDefinition = "datetime(6)")
        private LocalDateTime lastLogin;

        @Column(name = "is_superuser", nullable = false)
        private Boolean isSuperuser;

        @Column(name = "username", length = 150, nullable = false)
        private String username;

        @Column(name = "first_name", length = 150, nullable = false)
        private String firstName;

        @Column(name = "last_name", length = 150, nullable = false)
        private String lastName;

        @Column(name = "email", length = 254, nullable = false)
        private String email;

        @Column(name = "is_staff", nullable = false)
        private Boolean isStaff;

        @Column(name = "is_active", nullable = false)
        private Boolean isActive;

        @Column(name = "date_joined", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime dateJoined;
    }

    @Entity
    @Table(name = "horarios_semanales")
    public static class WeeklySchedule {
        @Id
        @Column(name = "user_id", nullable = false)
        private Integer userId;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "classes", nullable = false)
        private JsonNode classes;

        @Column(name = "updated_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime updatedAt;
    }

    @Entity
    @Table(name = "identificaciones_planta")
    public static class PlantIdentification {
        @Id
        @Column(name = "id", columnDefinition = "char(32)", nullable = false)
        private String id;

        @Column(name = "batch_id", length = 36, nullable = false)
        private String batchId;

        @Column(name = "scientific_name", length = 255, nullable = false)
        private String scientificName;

        @Column(name = "common_name", length = 255, nullable = false)
        private String commonName;

        @Column(name = "family", length = 255, nullable = false)
        private String family;

        @Column(name = "confidence", nullable = false)
        private Double confidence;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "result_json", nullable = false)
        private JsonNode resultJson;

        @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "user_id")
        private Integer userId;
    }

    @Entity
    @Table(name = "imagenes_planta")
    public static class PlantImage {
        @Id
        @Column(name = "id", columnDefinition = "char(36)", nullable = false)
        private String id;

        @Column(name = "batch_id", columnDefinition = "char(36)", nullable = false)
        private String batchId;

        @Column(name = "relative_path", length = 255, nullable = false)
        private String relativePath;

        @Column(name = "mime_type", length = 32, nullable = false)
        private String mimeType;

        @Column(name = "file_size", columnDefinition = "bigint unsigned", nullable = false)
        private Long fileSize;

        @Column(name = "width", columnDefinition = "int unsigned", nullable = false)
        private Integer width;

        @Column(name = "height", columnDefinition = "int unsigned", nullable = false)
        private Integer height;

        @Column(name = "processing_status", columnDefinition = "enum('pending','processed','failed')", nullable = false)
        private String processingStatus;

        @Column(name = "created_at", columnDefinition = "timestamp", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "user_id")
        private Integer userId;
    }

    @Entity
    @Table(name = "imagenes_progreso_chat")
    public static class ChatProgressImage {
        @Id
        @Column(name = "id", columnDefinition = "char(32)", nullable = false)
        private String id;

        @Column(name = "relative_path", length = 512, nullable = false)
        private String relativePath;

        @Column(name = "mime_type", length = 32, nullable = false)
        private String mimeType;

        @Column(name = "file_size", nullable = false)
        private Long fileSize;

        @Column(name = "caption", columnDefinition = "longtext", nullable = false)
        private String caption;

        @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "message_id", columnDefinition = "char(32)", nullable = false)
        private String messageId;

        @Column(name = "plant_id", columnDefinition = "char(32)", nullable = false)
        private String plantId;

        @Column(name = "user_id", nullable = false)
        private Integer userId;
    }

    @Entity
    @Table(name = "importaciones_horario_pendientes")
    public static class PendingScheduleImport {
        @Id
        @Column(name = "id", columnDefinition = "char(32)", nullable = false)
        private String id;

        @Column(name = "source_pdf", length = 100, nullable = false)
        private String sourcePdf;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "parsed_classes", nullable = false)
        private JsonNode parsedClasses;

        @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "user_id", nullable = false)
        private Integer userId;
    }

    @Entity
    @Table(name = "mensajes_chat")
    public static class ChatMessage {
        @Id
        @Column(name = "id", columnDefinition = "char(32)", nullable = false)
        private String id;

        @Column(name = "session_id", length = 64, nullable = false)
        private String sessionId;

        @Column(name = "role", length = 16, nullable = false)
        private String role;

        @Column(name = "content", columnDefinition = "longtext", nullable = false)
        private String content;

        @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "user_id")
        private Integer userId;
    }

    @Entity
    @Table(name = "perfiles_usuario")
    public static class UserProfile {
        @Id
        @Column(name = "id", nullable = false)
        private Long id;

        @Column(name = "role", length = 16, nullable = false)
        private String role;

        @Column(name = "user_id", nullable = false)
        private Integer userId;
    }

    @Entity
    @Table(name = "procesos_identificacion")
    public static class IdentificationRun {
        @Id
        @Column(name = "id", columnDefinition = "char(32)", nullable = false)
        private String id;

        @Column(name = "batch_id", length = 36, nullable = false)
        private String batchId;

        @Column(name = "organ", length = 16, nullable = false)
        private String organ;

        @Column(name = "status", length = 16, nullable = false)
        private String status;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "raw_json", nullable = false)
        private JsonNode rawJson;

        @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "user_id")
        private Integer userId;
    }

    @Entity
    @Table(name = "tareas_cuidado")
    public static class CarePlanTask {
        @Id
        @Column(name = "id", columnDefinition = "char(32)", nullable = false)
        private String id;

        @Column(name = "title", length = 180, nullable = false)
        private String title;

        @Column(name = "instructions", columnDefinition = "longtext", nullable = false)
        private String instructions;

        @Column(name = "day_of_week", columnDefinition = "smallint unsigned", nullable = false)
        private Short dayOfWeek;

        @Column(name = "start_time", columnDefinition = "time(6)", nullable = false)
        private LocalTime startTime;

        @Column(name = "duration_minutes", columnDefinition = "smallint unsigned", nullable = false)
        private Short durationMinutes;

        @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false)
        private LocalDateTime createdAt;

        @Column(name = "plant_id", columnDefinition = "char(32)", nullable = false)
        private String plantId;

        @Column(name = "user_id", nullable = false)
        private Integer userId;
    }
}
