package com.agronomia.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del panel a JSON uniforme. La base de datos caída se
 * informa como 503 para que el frontend muestre "servicio no disponible".
 */
@RestControllerAdvice
public class AdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

    @ExceptionHandler(AdminException.class)
    ResponseEntity<AdminDtos.ErrorResponse> handleAdmin(AdminException exception) {
        return ResponseEntity.status(exception.status())
                .body(new AdminDtos.ErrorResponse(false, exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AdminDtos.ErrorResponse> handleDatabase(DataAccessException exception) {
        log.error("Error de base de datos en el panel", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AdminDtos.ErrorResponse(false,
                        "La base de datos no está disponible. Revisa que MySQL de XAMPP esté encendido."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AdminDtos.ErrorResponse> handleBadJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new AdminDtos.ErrorResponse(false, "El cuerpo de la petición no es JSON válido."));
    }
}
