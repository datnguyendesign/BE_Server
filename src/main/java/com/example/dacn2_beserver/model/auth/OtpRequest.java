package com.example.dacn2_beserver.model.auth;

import com.example.dacn2_beserver.model.enums.OtpChannel;
import com.example.dacn2_beserver.model.enums.OtpPurpose;
import com.example.dacn2_beserver.model.enums.OtpStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("otp_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequest {

    @Id
    private String id;

    private OtpChannel channel;
    private OtpPurpose purpose;

    private String identifier;

    @Indexed
    private String identifierNormalized;

    private String codeHash;

    @Builder.Default
    private int attempts = 0;

    @Builder.Default
    private OtpStatus status = OtpStatus.PENDING;

    @Indexed(name = "otp_expires_ttl", expireAfter = "0s")
    @Builder.Default
    private Instant expiresAt = Instant.now().plusSeconds(5 * 60);

    private Instant verifiedAt;
    private Instant lockedAt;

    @Builder.Default
    private Instant createdAt = Instant.now();
}