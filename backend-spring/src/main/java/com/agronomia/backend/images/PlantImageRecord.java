package com.agronomia.backend.images;

public record PlantImageRecord(
        String id,
        String batchId,
        String relativePath,
        String mimeType,
        long fileSize,
        int width,
        int height
) { }
