package com.example.dacn2_beserver.dto.health;

import java.time.Instant;

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
public class StartWorkoutTrackingResponse {
    private String trackingId;
    private Instant startedAt;
}
