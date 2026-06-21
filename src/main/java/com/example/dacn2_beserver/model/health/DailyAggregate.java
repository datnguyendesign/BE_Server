package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.GeoPoint;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Document("daily_aggregates")
@CompoundIndex(name = "uq_user_date", def = "{'userId': 1, 'date': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAggregate {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
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

    private Map<String, Object> gpsSummary;
    private GeoPoint lastKnownLocation;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}