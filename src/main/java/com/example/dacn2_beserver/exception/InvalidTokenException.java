package com.example.dacn2_beserver.exception;

public class InvalidTokenException extends ApiException{
    public InvalidTokenException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }

    public InvalidTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }
}
