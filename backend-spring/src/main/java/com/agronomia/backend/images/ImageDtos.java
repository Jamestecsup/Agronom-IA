package com.agronomia.backend.images;

import java.util.List;

public final class ImageDtos {
    private ImageDtos() { }

    public record UploadedImage(String id, String path, String mime_type, long size) { }
    public record UploadResponse(boolean success, String batch_id, List<UploadedImage> images) { }
    public record ErrorResponse(boolean success, String error) { }
}
