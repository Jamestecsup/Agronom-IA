package com.agronomia.backend.images;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ImageDtos.ErrorResponse> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ImageDtos.ErrorResponse(false, exception.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ImageDtos.ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException exception) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(java.util.Set.of(org.springframework.http.HttpMethod.POST));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(new ImageDtos.ErrorResponse(false, "Método no permitido. Usa POST."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ImageDtos.ErrorResponse> handleTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ImageDtos.ErrorResponse(false, "La solicitud supera el límite de tamaño permitido."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ImageDtos.ErrorResponse> handleMissing(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(new ImageDtos.ErrorResponse(false,
                "Envía entre una y cinco imágenes en el campo photos[]."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ImageDtos.ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API error for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.internalServerError()
                .body(new ImageDtos.ErrorResponse(false, "No se pudo completar la solicitud."));
    }
}
