package com.example.dacn2_beserver.dto.health;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartRateReadingResponse {
    private String id;
    private int bpm;
    private Instant measuredAt;
}
