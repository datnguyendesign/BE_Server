package com.example.dacn2_beserver.exception;

public class OTPTooSoonException extends ApiException {
    public OTPTooSoonException(String message) {
        super(ErrorCode.OTP_TOO_SOON, message);
    }
}