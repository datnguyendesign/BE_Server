package com.example.dacn2_beserver.exception;

public class EmailAlreadyExistsException extends ApiException{
    public EmailAlreadyExistsException(String email) {
        super(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists: " + email);
    }

    public EmailAlreadyExistsException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
