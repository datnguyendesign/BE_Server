package com.example.dacn2_beserver.exception;

public class AiServiceException extends ApiException{
    public AiServiceException(String message) {
        super(ErrorCode.AI_SERVICE_ERROR, message);
    }
}
