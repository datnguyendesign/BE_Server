package com.example.dacn2_beserver.exception;

public class ForbiddenException extends ApiException{
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
