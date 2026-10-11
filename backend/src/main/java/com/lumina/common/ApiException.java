package com.lumina.common;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : details;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public List<String> details() { return details; }
}
