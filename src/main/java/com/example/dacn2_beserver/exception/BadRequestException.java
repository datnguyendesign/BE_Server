package com.example.dacn2_beserver.exception;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
