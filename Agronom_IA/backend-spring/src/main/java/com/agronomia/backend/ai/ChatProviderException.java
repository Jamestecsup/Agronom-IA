package com.agronomia.backend.ai;

import org.springframework.http.HttpStatus;

public class ChatProviderException extends RuntimeException {
    private final HttpStatus status;
    public ChatProviderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public ChatProviderException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
    public HttpStatus status() { return status; }
}
