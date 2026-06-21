package com.example.dacn2_beserver.dto.health;

import java.time.Instant;

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
public class WorkoutTrackingLiveResponse {
    private String trackingId;

    private Double distanceKm;
    private Long activeDurationMs;

    private Integer steps;
    private Integer caloriesOut;

    private Integer avgPaceSecPerKm;

    private Instant updatedAt;
}
