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
    private static final List<String> IA_TABLES = List.of("proveedores_ia", "preferencias_usuario", "uso_ia_diario");

    @Bean
    FlywayMigrationStrategy migrateExistingDjangoSchema() {
        return flyway -> {
            var source = flyway.getConfiguration().getDataSource();
            requireTables(source, REQUIRED_TABLES, "Ejecuta primero scripts\\migrar-db.ps1.");
            flyway.migrate();
            requireTables(source, IA_TABLES, "Comprueba que Flyway haya aplicado V2 y V3.");
        };
    }

    private static void requireTables(DataSource dataSource, List<String> required, String solution) {
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
        var missing = required.stream().filter(table -> !tables.contains(table)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Faltan tablas en MySQL (" + String.join(", ", missing)
                    + "). " + solution);
        }
    }
}
