package com.example.dacn2_beserver.model.user;

import com.example.dacn2_beserver.model.enums.Gender;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String fullName;
    private String avatarUrl;

    private Gender gender;      // optional
    private Date birthday;      // optional
    private Double heightCm;    // optional
    private Double weightKg;    // optional
}