package com.example.dacn2_beserver.exception;

public class UnauthorizedException extends ApiException{
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
