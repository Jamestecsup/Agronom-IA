package com.agronomia.backend.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.agronomia.backend.user")
public class UserApiExceptionHandler {
    @ExceptionHandler(UserApiException.class)
    ResponseEntity<Map<String, Object>> handle(UserApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("success", false, "error", e.getMessage()));
    }
}
