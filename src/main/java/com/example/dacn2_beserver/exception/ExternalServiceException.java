package com.example.dacn2_beserver.exception;

public class ExternalServiceException extends ApiException{
    public ExternalServiceException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, message);
    }
}
