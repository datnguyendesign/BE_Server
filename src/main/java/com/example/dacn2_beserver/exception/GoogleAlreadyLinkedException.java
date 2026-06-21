package com.example.dacn2_beserver.exception;

public class GoogleAlreadyLinkedException extends ApiException {
    public GoogleAlreadyLinkedException(String message) {
        super(ErrorCode.GOOGLE_ALREADY_LINKED, message);
    }
}