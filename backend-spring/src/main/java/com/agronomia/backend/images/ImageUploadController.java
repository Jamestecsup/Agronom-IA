package com.agronomia.backend.images;

import com.agronomia.backend.user.UserAccounts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/agronomia-api/api")
public class ImageUploadController {
    private final ImageBatchService batchService;

    private final UserAccounts accounts;

    public ImageUploadController(ImageBatchService batchService, UserAccounts accounts) {
        this.batchService = batchService;
        this.accounts = accounts;
    }

    @PostMapping("/images.php")
    public ResponseEntity<ImageDtos.UploadResponse> upload(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "photos[]", required = false) List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.upload(accounts.requireUser(authorization), photos));
    }
}
