package com.agronomia.backend.user;

import com.agronomia.backend.images.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class UserFiles {
    private final Path root;

    public UserFiles(@Value("${plant.storage.directory}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public Path resolve(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw missing();
        return path;
    }

    public Path existing(String relative) {
        try {
            Path path = resolve(relative).toRealPath();
            if (!path.startsWith(root.toRealPath()) || !Files.isRegularFile(path)) throw missing();
            return path;
        } catch (IOException error) {
            throw missing();
        }
    }

    public ResponseEntity<FileSystemResource> response(String relative, String mime) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
                .header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(existing(relative)));
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "No se encontró la imagen.");
    }
}
