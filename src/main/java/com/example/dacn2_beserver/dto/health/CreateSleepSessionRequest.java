package com.example.dacn2_beserver.dto.health;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSleepSessionRequest {

    @NotNull
    private TimeRangeDto time;

    private List<SleepSegmentDto> segments;
    private SourceMetaDto meta;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SleepSegmentDto {
        private String stage; // SleepStage name
        private java.time.Instant startAt;
        private java.time.Instant endAt;
    }
}