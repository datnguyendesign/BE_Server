package com.example.dacn2_beserver.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String fullName;
    private String avatarUrl;

    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Past
    private Date birthDate;

    @PositiveOrZero
    private Double heightCm;
    @PositiveOrZero
    private Double weightKg;
}