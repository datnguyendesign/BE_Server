package com.example.dacn2_beserver.dto.media;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresignGetResponse {
    private String url;
    private Instant expiresAt;
}