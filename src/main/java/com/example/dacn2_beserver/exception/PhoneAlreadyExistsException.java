package com.example.dacn2_beserver.exception;

public class PhoneAlreadyExistsException extends ApiException {
    public PhoneAlreadyExistsException(String message) {
        super(ErrorCode.PHONE_ALREADY_EXISTS, message);
    }
}