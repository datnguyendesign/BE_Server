package com.example.dacn2_beserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleVerifyRequest {
    @NotBlank
    private String idToken;   // token từ Google
    private String deviceId;
}