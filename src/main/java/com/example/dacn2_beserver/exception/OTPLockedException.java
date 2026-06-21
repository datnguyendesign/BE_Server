package com.example.dacn2_beserver.exception;

public class OTPLockedException extends ApiException {
    public OTPLockedException(String message) {
        super(ErrorCode.OTP_LOCKED, message);
    }
}