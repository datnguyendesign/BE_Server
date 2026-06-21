package com.example.dacn2_beserver.model.auth;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("user_credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredential {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private String type = "password";

    private String passwordHash;
    private Instant passwordUpdatedAt;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}