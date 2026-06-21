package com.example.dacn2_beserver.dto.user;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGoalsRequest {
    @PositiveOrZero
    private Integer dailySteps;

    @PositiveOrZero
    private Integer dailyCaloriesIn;

    @PositiveOrZero
    private Integer dailyCaloriesOut;

    @PositiveOrZero
    private Integer dailyWaterMl;

    @PositiveOrZero
    private Double targetWeightKg;
}