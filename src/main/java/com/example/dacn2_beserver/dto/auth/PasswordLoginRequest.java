package com.example.dacn2_beserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordLoginRequest {
    @NotBlank
    private String identifier; // email hoặc phone
    @NotBlank
    private String password;
    @NotBlank
    private String deviceId;
}