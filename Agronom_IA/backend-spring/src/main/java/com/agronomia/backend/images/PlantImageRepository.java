package com.agronomia.backend.images;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlantImageRepository {
    private static final String INSERT_SQL = """
            INSERT INTO plant_images (id, batch_id, relative_path, mime_type, file_size, width, height)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PlantImageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(List<PlantImageRecord> records) {
        jdbcTemplate.batchUpdate(INSERT_SQL, records, records.size(), (statement, image) -> {
            statement.setString(1, image.id());
            statement.setString(2, image.batchId());
            statement.setString(3, image.relativePath());
            statement.setString(4, image.mimeType());
            statement.setLong(5, image.fileSize());
            statement.setInt(6, image.width());
            statement.setInt(7, image.height());
        });
    }
}
