package com.example.dacn2_beserver.exception;

public class UsernameAlreadyExistsException extends ApiException{
    public UsernameAlreadyExistsException(String username) {
        super(ErrorCode.USERNAME_ALREADY_EXISTS, "Username already exists: " + username);
    }

    public UsernameAlreadyExistsException() {
        super(ErrorCode.USERNAME_ALREADY_EXISTS);
    }
}
