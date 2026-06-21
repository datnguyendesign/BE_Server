package com.example.dacn2_beserver.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // ==== COMMON 4xx ====
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Bad request"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token is invalid or expired"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),

    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "Conflict"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already exists"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS", "Phone number already exists"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "Username already exists"),

    // ==== COMMON 5xx ====
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal server error"),
    MONGO_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "MONGO_ERROR", "MongoDB error"),
    REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_ERROR", "Redis error"),

    // ==== EXTERNAL SERVICES (AI server, Google Fit, Apple Health...) ====
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR", "External service error"),
    AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR", "AI service error"),
    GOOGLE_FIT_ERROR(HttpStatus.BAD_GATEWAY, "GOOGLE_FIT_ERROR", "Google Fit service error"),
    APPLE_HEALTH_ERROR(HttpStatus.BAD_GATEWAY, "APPLE_HEALTH_ERROR", "Apple Health service error"),

    // ==== OTP ERRORS ====
    OTP_INVALID(HttpStatus.BAD_REQUEST, "OTP_INVALID", "OTP code is invalid"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "OTP code is expired"),
    OTP_LOCKED(HttpStatus.BAD_REQUEST, "OTP_LOCKED", "OTP is locked due to too many attempts"),
    OTP_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_SOON", "Please wait before requesting another OTP"),

    // ==== GOOGLE AUTH ERRORS ====
    GOOGLE_ID_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "GOOGLE_ID_TOKEN_INVALID", "Google id_token is invalid"),
    LINK_TICKET_INVALID(HttpStatus.BAD_REQUEST, "LINK_TICKET_INVALID", "Link ticket is invalid"),
    LINK_TICKET_EXPIRED(HttpStatus.BAD_REQUEST, "LINK_TICKET_EXPIRED", "Link ticket is expired"),
    GOOGLE_ALREADY_LINKED(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED", "This Google account is already linked"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}