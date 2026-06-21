package com.example.dacn2_beserver.model.common;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeRange {
    private Instant startAt;
    private Instant endAt;
}