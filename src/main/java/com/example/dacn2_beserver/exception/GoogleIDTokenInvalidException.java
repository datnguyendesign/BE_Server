package com.example.dacn2_beserver.exception;

public class GoogleIDTokenInvalidException extends ApiException {
    public GoogleIDTokenInvalidException(String message) {
        super(ErrorCode.GOOGLE_ID_TOKEN_INVALID, message);
    }
}