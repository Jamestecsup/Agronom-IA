package com.agronomia.backend.images;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/agronomia-api/api")
public class ImageUploadController {
    private final ImageBatchService batchService;

    public ImageUploadController(ImageBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/images.php")
    public ResponseEntity<ImageDtos.UploadResponse> upload(
            @RequestParam(name = "photos[]", required = false) List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.upload(photos));
    }
}
