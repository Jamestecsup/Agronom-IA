package com.agronomia.backend.admin;

import java.util.List;

/**
 * Contratos JSON del panel de administración (camelCase para React).
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(
            boolean success,
            String token,
            String username,
            String role,
            long expiresAt
    ) {
    }

    public record MeResponse(String username, String role) {
    }

    public record ErrorResponse(boolean success, String error) {
    }

    public record UserRow(
            long id,
            String username,
            String email,
            String role,
            boolean active,
            String createdAt
    ) {
    }

    public record UpdateUserRequest(String role, Boolean active) {
    }

    public record PlantRow(
            String id,
            String scientificName,
            String commonName,
            String family,
            double confidence,
            String createdAt
    ) {
    }

    public record ChatRow(
            String id,
            String sessionId,
            String role,
            String content,
            String createdAt
    ) {
    }

    public record SpeciesCount(String scientificName, String commonName, long count) {
    }

    public record DayCount(String date, long count) {
    }

    public record StatsResponse(
            long users,
            long plants,
            long chats,
            long images,
            List<SpeciesCount> topSpecies,
            List<DayCount> daily
    ) {
    }

    public record HealthResponse(String status, String service, boolean database) {
    }
}
