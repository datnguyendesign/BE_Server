package com.example.dacn2_beserver.dto.user;

import com.example.dacn2_beserver.model.enums.Role;
import com.example.dacn2_beserver.model.enums.UserStatus;
import lombok.*;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String username;
    private String primaryEmail;

    private UserProfileDto profile;
    private UserSettingsDto settings;
    private UserGoalsDto goals;

    private UserStatus status;
    private Set<Role> roles;

    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
}