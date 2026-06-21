package com.example.dacn2_beserver.model.health;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document("workout_points")
@CompoundIndex(name = "uniq_tracking_ts", def = "{'trackingId': 1, 'ts': 1}", unique = true)
@CompoundIndex(name = "idx_tracking_ts", def = "{'trackingId': 1, 'ts': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPoint {

    @Id
    private String id;

    @Indexed
    private String trackingId;

    @Indexed
    private String userId;

    private Instant ts;
    private double lat;
    private double lng;

    private Double accuracyM;
    private Double speedMps;
}
