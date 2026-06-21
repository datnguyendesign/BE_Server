package com.example.dacn2_beserver.dto.health;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDaySummaryResponse {

    private LocalDate date;

    private int steps;
    private double distanceKm;

    private int caloriesIn;
    private int caloriesOut;

    private int waterMl;

    private int sleepMinutes;
    private int deepMinutes;
    private int remMinutes;
    private int lightMinutes;
    private int awakeMinutes;

    private List<String> highlights;
    private String summary;

    private boolean isFuture;
}