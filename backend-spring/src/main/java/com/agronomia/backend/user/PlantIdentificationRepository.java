package com.agronomia.backend.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PlantIdentificationRepository {
    private final JdbcTemplate jdbc;

    public PlantIdentificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PlantRow(
            String id,
            String batchId,
            String scientificName,
            String commonName,
            String family,
            double confidence,
            String resultJson,
            LocalDateTime createdAt
    ) { }

    public List<PlantRow> findAllByUserId(long userId) {
        return jdbc.query(
                "SELECT id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at " +
                        "FROM plant_identifications WHERE user_id=? ORDER BY created_at DESC",
                (rs, i) -> mapPlant(rs.getString("id"), rs.getString("batch_id"),
                        rs.getString("scientific_name"), rs.getString("common_name"),
                        rs.getString("family"), rs.getDouble("confidence"),
                        rs.getString("result_json"), rs.getTimestamp("created_at")),
                userId);
    }

    public Optional<PlantRow> findByIdAndUserId(String plantId, long userId) {
        return jdbc.query(
                        "SELECT id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at " +
                                "FROM plant_identifications WHERE id=? AND user_id=?",
                        (rs, i) -> mapPlant(rs.getString("id"), rs.getString("batch_id"),
                                rs.getString("scientific_name"), rs.getString("common_name"),
                                rs.getString("family"), rs.getDouble("confidence"),
                                rs.getString("result_json"), rs.getTimestamp("created_at")),
                        plantId, userId)
                .stream()
                .findFirst();
    }

    public void insertPlant(String id, long userId, String batchId, String scientificName,
                            String commonName, String family, double confidence,
                            String resultJson, LocalDateTime createdAt) {
        jdbc.update(
                "INSERT INTO plant_identifications (id,user_id,batch_id,scientific_name,common_name,family,confidence,result_json,created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                id, userId, batchId, scientificName, commonName, family, confidence,
                resultJson, Timestamp.valueOf(createdAt));
    }

    public void insertRun(String id, long userId, String batchId, String organ, String status,
                          String rawJson, LocalDateTime createdAt) {
        jdbc.update(
                "INSERT INTO identification_runs (id,user_id,batch_id,organ,status,raw_json,created_at) " +
                        "VALUES (?,?,?,?,?,?,?)",
                id, userId, batchId, organ, status, rawJson, Timestamp.valueOf(createdAt));
    }

    public Optional<String> findLatestRunRawJson(long userId, String batchId) {
        return jdbc.query(
                        "SELECT raw_json FROM identification_runs WHERE user_id=? AND batch_id=? " +
                                "ORDER BY created_at DESC LIMIT 1",
                        (rs, i) -> rs.getString("raw_json"), userId, batchId)
                .stream()
                .findFirst();
    }

    private PlantRow mapPlant(String id, String batchId, String scientificName, String commonName,
                              String family, double confidence, String resultJson, Timestamp createdAt) {
        return new PlantRow(id, batchId, scientificName, commonName, family, confidence,
                resultJson, createdAt.toLocalDateTime());
    }
}
