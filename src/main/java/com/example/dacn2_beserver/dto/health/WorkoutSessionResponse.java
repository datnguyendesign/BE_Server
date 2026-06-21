package com.example.dacn2_beserver.dto.health;

import java.time.Instant;
import java.util.List;

import com.example.dacn2_beserver.model.enums.RecordStatus;
import com.example.dacn2_beserver.model.enums.WorkoutType;

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
public class WorkoutSessionResponse {
    private String id;
    private String userId;

    private WorkoutType workoutType;
    private TimeRangeDto time;

    private Double distanceKm;
    private Integer steps;
    private Integer caloriesOut;

    private List<GeoPointDto> routeSample;
    private GeoPointDto startLocation;
    private GeoPointDto endLocation;

    private RecordStatus status;
    private SourceMetaDto meta;

    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoPointDto {
        private Double lat;
        private Double lng;
    }
}
