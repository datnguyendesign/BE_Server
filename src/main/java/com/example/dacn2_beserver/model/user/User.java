package com.example.dacn2_beserver.model.user;

import com.example.dacn2_beserver.model.enums.Role;
import com.example.dacn2_beserver.model.enums.UserStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document("users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String username;

    @Indexed(unique = true, sparse = true)
    private String primaryEmail;

    private UserProfile profile;

    @Builder.Default
    private UserSettings settings = UserSettings.builder().build();

    @Builder.Default
    private UserGoals goals = UserGoals.builder().build();

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Builder.Default
    private Set<Role> roles = new HashSet<>(Set.of(Role.USER));

    private Instant lastLoginAt;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant deletedAt;
}