package com.example.dacn2_beserver.exception;

public class InvalidCredentialsException extends ApiException{
    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
    }
}
