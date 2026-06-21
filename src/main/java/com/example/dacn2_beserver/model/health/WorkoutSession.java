package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.GeoPoint;
import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import com.example.dacn2_beserver.model.enums.WorkoutType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("workouts")
@CompoundIndex(name = "idx_user_start", def = "{'userId': 1, 'time.startAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private WorkoutType workoutType;

    private TimeRange time;

    private Double distanceKm;
    private Integer steps;
    private Integer caloriesOut;

    private Integer avgHeartRate;
    private Integer maxHeartRate;

    private List<GeoPoint> routeSample;
    private GeoPoint startLocation;
    private GeoPoint endLocation;

    @Builder.Default
    private RecordStatus status = RecordStatus.CONFIRMED;

    private SourceMeta meta;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}