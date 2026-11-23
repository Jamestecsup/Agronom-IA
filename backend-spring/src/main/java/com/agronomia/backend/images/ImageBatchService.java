package com.agronomia.backend.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
        return upload(files, null);
    }

    public ImageDtos.UploadResponse upload(List<MultipartFile> files, Long userId) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Env?a entre una y cinco im?genes en el campo photos[].");
        }
        if (files.size() > MAX_PHOTOS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La operaci?n debe contener entre una y cinco im?genes.");
        }

        List<ImageMetadata> validated = files.stream().map(validator::validate).toList();
        String batchId = UUID.randomUUID().toString();
        List<PlantImageRecord> records = storage.store(batchId, validated);
        if (userId != null) {
            List<PlantImageRecord> withUser = new ArrayList<>(records.size());
            for (PlantImageRecord r : records) {
                withUser.add(new PlantImageRecord(r.id(), r.batchId(), r.relativePath(), r.mimeType(), r.fileSize(), r.width(), r.height(), userId));
            }
            records = withUser;
        }
        try {
            List<PlantImageRecord> persisted = records;
            transaction.executeWithoutResult(status -> repository.insertAll(persisted));
            List<ImageDtos.UploadedImage> images = records.stream()
                    .map(row -> new ImageDtos.UploadedImage(row.id(), row.relativePath(), row.mimeType(), row.fileSize()))
                    .toList();
            return new ImageDtos.UploadResponse(true, batchId, images);
        } catch (DataAccessException exception) {
            storage.deleteBatch(batchId);
            log.error("Database rejected an image batch (batch={})", batchId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se complet? el almacenamiento. No se confirm? ning?n ?xito; comprueba la conexi?n con MySQL.");
        } catch (RuntimeException exception) {
            storage.deleteBatch(batchId);
            log.error("Image batch persistence failed (batch={})", batchId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se complet? el almacenamiento. No se confirm? ning?n ?xito.");
        }
    }
}
