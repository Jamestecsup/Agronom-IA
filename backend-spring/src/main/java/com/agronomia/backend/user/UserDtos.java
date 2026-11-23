package com.agronomia.backend.user;

import java.util.List;
import java.util.Map;

public final class UserDtos {
    private UserDtos() {}

    public record AuthRequest(String username, String password, String email) {}
    public record AuthResponse(boolean success, String username, String role, String token, String error) {}
    public record UploadImage(String id, String path, String mime_type, long size) {}
    public record UploadResponse(boolean success, String batch_id, List<UploadImage> images, String error) {}
}
