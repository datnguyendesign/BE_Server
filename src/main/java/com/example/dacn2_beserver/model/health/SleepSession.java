package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import com.example.dacn2_beserver.model.enums.SleepStage;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("sleep_sessions")
@CompoundIndex(name = "idx_user_start", def = "{'userId': 1, 'time.startAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SleepSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private TimeRange time;

    private Integer totalMinutes;
    private Integer deepMinutes;
    private Integer remMinutes;
    private Integer lightMinutes;
    private Integer awakeMinutes;

    private List<SleepSegment> segments;

    @Builder.Default
    private RecordStatus status = RecordStatus.CONFIRMED;

    private SourceMeta meta;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SleepSegment {
        private SleepStage stage;
        private Instant startAt;
        private Instant endAt;
    }
}