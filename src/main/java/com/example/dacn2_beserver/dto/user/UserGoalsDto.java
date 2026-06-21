package com.example.dacn2_beserver.dto.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGoalsDto {
    private Integer dailySteps;
    private Integer dailyCaloriesIn;
    private Integer dailyCaloriesOut;
    private Integer dailyWaterMl;

    private Double targetWeightKg;
}