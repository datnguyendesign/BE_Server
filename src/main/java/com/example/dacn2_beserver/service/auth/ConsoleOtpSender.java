package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.model.enums.OtpChannel;
import org.springframework.stereotype.Component;

@Component
public class ConsoleOtpSender implements OtpSender {
    @Override
    public void send(OtpChannel channel, String identifier, String code) {
        System.out.println("[OTP][" + channel + "] to=" + identifier + " code=" + code);
    }
}