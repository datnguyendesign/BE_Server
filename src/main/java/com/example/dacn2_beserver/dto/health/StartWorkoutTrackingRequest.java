package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.WorkoutType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartWorkoutTrackingRequest {
    @NotNull
    private WorkoutType workoutType; // WALK | RUN
}
