package com.agronomia.backend.admin;

import org.springframework.http.HttpStatus;

/** Error de negocio del panel de administración. */
public class AdminException extends RuntimeException {

    private final HttpStatus status;

    public AdminException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
