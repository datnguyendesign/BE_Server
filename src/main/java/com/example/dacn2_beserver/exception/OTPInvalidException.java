package com.example.dacn2_beserver.exception;

public class OTPInvalidException extends ApiException {
    public OTPInvalidException(String message) {
        super(ErrorCode.OTP_INVALID, message);
    }
}