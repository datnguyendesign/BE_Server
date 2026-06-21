package com.example.dacn2_beserver.exception;

public class ConflictException extends ApiException{
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
