package com.example.dacn2_beserver.dto.auth;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokensResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresInSeconds;
}