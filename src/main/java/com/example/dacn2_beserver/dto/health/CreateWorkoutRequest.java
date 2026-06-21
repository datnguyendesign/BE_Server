package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.WorkoutType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkoutRequest {
    @NotNull
    private WorkoutType workoutType;
    
    @NotNull
    private TimeRangeDto time;

    private Double distanceKm;
    private Integer steps;
    private Integer caloriesOut;

    private Integer avgHeartRate;
    private Integer maxHeartRate;

    private SourceMetaDto meta;
}