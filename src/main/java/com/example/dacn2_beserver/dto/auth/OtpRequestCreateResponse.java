package com.example.dacn2_beserver.dto.auth;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequestCreateResponse {
    private String otpRequestId;
    private Long expiresInSeconds;
}