package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.GeoPoint;
import com.example.dacn2_beserver.model.enums.WorkoutTrackingStatus;
import com.example.dacn2_beserver.model.enums.WorkoutType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("workout_trackings")
@CompoundIndex(name = "idx_user_status", def = "{'userId': 1, 'status': 1, 'startedAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutTracking {

    @Id
    private String id;

    @Indexed
    private String userId;

    private WorkoutType workoutType;

    @Builder.Default
    private WorkoutTrackingStatus status = WorkoutTrackingStatus.ACTIVE;

    private Instant startedAt;
    private Instant endedAt;

    // pause bookkeeping
    @Builder.Default
    private long totalPausedMs = 0L;
    private Instant pausedAt;

    // summary (incremental)
    @Builder.Default
    private double distanceKm = 0.0;
    @Builder.Default
    private Integer steps = 0;

    // for route sample (store ~100 pts for quick draw)
    @Builder.Default
    private List<GeoPoint> routeSample = new ArrayList<>();
    private GeoPoint startLocation;
    private GeoPoint endLocation;

    private LastPoint lastPoint;

    // sampling counter
    @Builder.Default
    private long pointCount = 0L;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastPoint {
        private Instant ts;
        private double lat;
        private double lng;
        private Double accuracyM;
    }
}
