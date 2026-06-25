package com.example.dacn2_beserver.dto.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private String fullName;
    private String avatarUrl;

    private String gender;
    private String birthDate;          // yyyy-MM-dd
    private Double heightCm;
    private Double weightKg;
    private String bloodType;
    private java.util.List<String> conditions;
}