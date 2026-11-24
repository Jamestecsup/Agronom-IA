package com.agronomia.backend.persistence;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;

@Configuration
public class SchemaBootstrapConfig {
    private static final List<String> REQUIRED_TABLES = List.of(
            "auth_user", "perfiles_usuario", "imagenes_planta",
            "procesos_identificacion", "identificaciones_planta", "mensajes_chat",
            "imagenes_progreso_chat", "importaciones_horario_pendientes",
            "horarios_semanales", "tareas_cuidado"
    );

    @Bean
    FlywayMigrationStrategy migrateExistingDjangoSchema() {
        return flyway -> {
            requireDjangoTables(flyway.getConfiguration().getDataSource());
            flyway.migrate();
        };
    }

    private static void requireDjangoTables(DataSource dataSource) {
        var tables = new HashSet<String>();
        try (var connection = dataSource.getConnection();
             var rows = connection.getMetaData().getTables(connection.getCatalog(), null, "%", null)) {
            while (rows.next()) {
                String kind = rows.getString("TABLE_TYPE");
                if ("TABLE".equals(kind) || "BASE TABLE".equals(kind)) {
                    tables.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo verificar el esquema MySQL antes de iniciar Flyway.", error);
        }
        var missing = REQUIRED_TABLES.stream().filter(table -> !tables.contains(table)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Faltan tablas en MySQL (" + String.join(", ", missing)
                    + "). Ejecuta primero scripts\\migrar-db.ps1.");
        }
    }
}
