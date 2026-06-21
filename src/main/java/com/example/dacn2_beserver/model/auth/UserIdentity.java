package com.example.dacn2_beserver.model.auth;

import com.example.dacn2_beserver.model.enums.IdentityProvider;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("user_identities")
@CompoundIndex(name = "uq_provider_normalized", def = "{'provider': 1, 'normalized': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private IdentityProvider provider;

    private String identifier;   // raw (email/phone/sub)
    private String normalized;   // email lowercase, phone E.164, google sub

    @Builder.Default
    private boolean verified = false;

    // Google-specific (optional)
    @Indexed(unique = true, sparse = true)
    private String providerAccountId; // google sub (nếu bạn muốn tách rõ)
    private String emailAtProvider;   // email Google trả về tại thời điểm login
    private Boolean emailVerifiedAtProvider;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}