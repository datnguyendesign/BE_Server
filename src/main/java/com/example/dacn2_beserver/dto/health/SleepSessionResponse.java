package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.RecordStatus;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SleepSessionResponse {
    private String id;
    private String userId;

    private TimeRangeDto time;

    private Integer totalMinutes;
    private Integer deepMinutes;
    private Integer remMinutes;
    private Integer lightMinutes;
    private Integer awakeMinutes;

    private List<SleepSegmentDto> segments;

    private RecordStatus status;
    private SourceMetaDto meta;

    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SleepSegmentDto {
        private String stage; // SleepStage name
        private Instant startAt;
        private Instant endAt;
    }
}