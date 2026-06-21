package com.example.dacn2_beserver.dto.health;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeRangeDto {
    private Instant startAt;
    private Instant endAt;
}