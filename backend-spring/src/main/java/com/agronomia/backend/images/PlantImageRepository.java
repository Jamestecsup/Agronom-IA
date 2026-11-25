package com.agronomia.backend.images;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlantImageRepository {
    private static final String INSERT_SQL = """
            INSERT INTO plant_images (id, batch_id, relative_path, mime_type, file_size, width, height, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
            if (image.userId() == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, image.userId());
        });
    }

    public List<String> findRelativePathsByBatchIdAndUserId(String batchId, long userId) {
        return jdbcTemplate.query(
                "SELECT relative_path FROM plant_images WHERE batch_id=? AND user_id=? ORDER BY created_at ASC",
                (rs, rowNum) -> rs.getString("relative_path"), batchId, userId);
    }

    public java.util.Optional<String> findFirstRelativePathByBatchIdAndUserId(String batchId, long userId) {
        return jdbcTemplate.query(
                        "SELECT relative_path FROM plant_images WHERE batch_id=? AND user_id=? " +
                                "ORDER BY created_at ASC LIMIT 1",
                        (rs, rowNum) -> rs.getString("relative_path"), batchId, userId)
                .stream()
                .findFirst();
    }
}
