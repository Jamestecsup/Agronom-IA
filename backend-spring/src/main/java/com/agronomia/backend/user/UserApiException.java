package com.agronomia.backend.user;

import org.springframework.http.HttpStatus;

public class UserApiException extends RuntimeException {
    private final HttpStatus status;
    public UserApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
