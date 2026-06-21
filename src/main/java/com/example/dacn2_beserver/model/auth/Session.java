package com.example.dacn2_beserver.model.auth;

import com.example.dacn2_beserver.model.enums.DevicePlatform;
import com.example.dacn2_beserver.model.enums.SessionStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String deviceId;

    private DevicePlatform platform;

    private String refreshTokenHash;

    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Indexed(name = "session_expires_ttl", expireAfter = "0s")
    private Instant expiresAt;

    private Instant revokedAt;
    private String revokedReason;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant lastSeenAt = Instant.now();
}