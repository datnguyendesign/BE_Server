package com.example.dacn2_beserver.exception;

public class UserNotFoundException extends ApiException{
    public UserNotFoundException(String message) {
        super(ErrorCode.USER_NOT_FOUND, message);
    }

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
