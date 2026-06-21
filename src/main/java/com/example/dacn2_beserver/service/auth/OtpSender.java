package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.model.enums.OtpChannel;

public interface OtpSender {
    void send(OtpChannel channel, String identifier, String code);
}