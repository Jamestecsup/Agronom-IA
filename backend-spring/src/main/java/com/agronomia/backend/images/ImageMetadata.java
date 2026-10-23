package com.agronomia.backend.images;

import org.springframework.web.multipart.MultipartFile;

public record ImageMetadata(
        MultipartFile source,
        String id,
        String mimeType,
        String extension,
        long size,
        int width,
        int height
) { }
