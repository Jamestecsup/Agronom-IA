package com.agronomia.backend.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class ImageBatchService {
    private static final Logger log = LoggerFactory.getLogger(ImageBatchService.class);
    public static final int MAX_PHOTOS = 5;

    private final ImageValidator validator;
    private final ImageFileStorage storage;
    private final PlantImageRepository repository;
    private final TransactionTemplate transaction;

    public ImageBatchService(ImageValidator validator, ImageFileStorage storage,
                             PlantImageRepository repository, PlatformTransactionManager transactionManager) {
        this.validator = validator;
        this.storage = storage;
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ImageDtos.UploadResponse upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Envía entre una y cinco imágenes en el campo photos[].");
        }
        if (files.size() > MAX_PHOTOS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La operación debe contener entre una y cinco imágenes.");
        }

        List<ImageMetadata> validated = files.stream().map(validator::validate).toList();
        String batchId = UUID.randomUUID().toString();
        List<PlantImageRecord> records = storage.store(batchId, validated);
        try {
            transaction.executeWithoutResult(status -> repository.insertAll(records));
            List<ImageDtos.UploadedImage> images = records.stream()
                    .map(row -> new ImageDtos.UploadedImage(row.id(), row.relativePath(), row.mimeType(), row.fileSize()))
                    .toList();
            return new ImageDtos.UploadResponse(true, batchId, images);
        } catch (DataAccessException exception) {
            storage.deleteBatch(batchId);
            log.error("Database rejected an image batch (batch={})", batchId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se completó el almacenamiento. No se confirmó ningún éxito; comprueba la conexión con MySQL.");
        } catch (RuntimeException exception) {
            storage.deleteBatch(batchId);
            log.error("Image batch persistence failed (batch={})", batchId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se completó el almacenamiento. No se confirmó ningún éxito.");
        }
    }
}
