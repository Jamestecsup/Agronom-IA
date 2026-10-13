package com.agronomia.backend.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ImageFileStorage {
    private static final Logger log = LoggerFactory.getLogger(ImageFileStorage.class);
    private final Path root;

    public ImageFileStorage(@Value("${plant.storage.directory}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public List<PlantImageRecord> store(String batchId, List<ImageMetadata> images) {
        Path batchDirectory = safeBatchDirectory(batchId);
        boolean createdBatchDirectory = false;
        try {
            Files.createDirectories(root);
            FileStore fileStore = Files.getFileStore(root);
            long requiredBytes = images.stream().mapToLong(ImageMetadata::size).sum();
            if (fileStore.getUsableSpace() < requiredBytes) {
                throw new ApiException(org.springframework.http.HttpStatus.INSUFFICIENT_STORAGE, "No hay espacio suficiente para almacenar el lote de imágenes.");
            }
            Files.createDirectory(batchDirectory);
            createdBatchDirectory = true;
            List<PlantImageRecord> records = new ArrayList<>(images.size());
            for (ImageMetadata image : images) {
                String filename = image.id() + "." + image.extension();
                Path target = batchDirectory.resolve(filename).normalize();
                if (!target.startsWith(batchDirectory)) throw new IOException("Ruta de archivo fuera de la carpeta de lote.");
                MultipartFile source = image.source();
                try (InputStream input = source.getInputStream()) {
                    long copiedBytes = Files.copy(input, target);
                    if (copiedBytes != image.size()) throw new IOException("El archivo recibido cambió durante la escritura.");
                }
                records.add(new PlantImageRecord(image.id(), batchId, batchId + "/" + filename,
                        image.mimeType(), image.size(), image.width(), image.height()));
            }
            return records;
        } catch (ApiException exception) {
            if (createdBatchDirectory) deleteBatch(batchId);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (createdBatchDirectory) deleteBatch(batchId);
            log.warn("Image batch could not be written to local storage (batch={})", batchId, exception);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo escribir el lote en el almacenamiento local.");
        }
    }

    public void deleteBatch(String batchId) {
        Path directory = safeBatchDirectory(batchId);
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { log.error("Could not clean failed image batch path {}", path, exception); }
            });
        } catch (IOException exception) {
            log.error("Could not inspect failed image batch directory {}", directory, exception);
        }
    }

    private Path safeBatchDirectory(String batchId) {
        Path directory = root.resolve(batchId).normalize();
        if (!directory.startsWith(root) || !batchId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Identificador de lote inválido.");
        }
        return directory;
    }
}
