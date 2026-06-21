package com.example.dacn2_beserver.exception;

public class OTPExpiredException extends ApiException {
    public OTPExpiredException(String message) {
        super(ErrorCode.OTP_EXPIRED, message);
    }
}