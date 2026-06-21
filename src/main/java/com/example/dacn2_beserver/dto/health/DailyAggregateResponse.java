package com.example.dacn2_beserver.dto.health;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAggregateResponse {
    private String id;
    private String userId;
    private LocalDate date;

    private Integer steps;
    private Double distanceKm;

    private Integer caloriesIn;
    private Integer caloriesOut;

    private Integer avgHeartRate;
    private Integer maxHeartRate;
    private Integer minHeartRate;

    private Integer waterMl;

    private Integer sleepMinutes;
    private Integer deepMinutes;
    private Integer remMinutes;
    private Integer lightMinutes;
    private Integer awakeMinutes;

    private List<String> highlights;
    private String summary;

    private Instant createdAt;
    private Instant updatedAt;
}