package com.example.dacn2_beserver.dto.media;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresignPutResponse {
    private String objectKey;
    private String uploadUrl;
    private Instant expiresAt;

    // only used for chat media
    private String publicUrl;
}