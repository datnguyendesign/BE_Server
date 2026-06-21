package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.DailyAggregateResponse;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final DailyAggregateRepository dailyAggregateRepository;

    public DailyAggregateResponse getByDate(String userId, LocalDate date) {
        DailyAggregate agg = dailyAggregateRepository.findByUserIdAndDate(userId, date)
                .orElseGet(() -> DailyAggregate.builder().userId(userId).date(date).build());
        return toResponse(agg);
    }

    public List<DailyAggregateResponse> getRange(String userId, LocalDate from, LocalDate to) {
        return dailyAggregateRepository.findAllByUserIdAndDateBetween(userId, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private DailyAggregateResponse toResponse(DailyAggregate agg) {
        return DailyAggregateResponse.builder()
                .date(agg.getDate())
                .steps(nvl(agg.getSteps()))
                .distanceKm(nvl(agg.getDistanceKm()))
                .waterMl(nvl(agg.getWaterMl()))
                .sleepMinutes(nvl(agg.getSleepMinutes()))
                .deepMinutes(nvl(agg.getDeepMinutes()))
                .remMinutes(nvl(agg.getRemMinutes()))
                .lightMinutes(nvl(agg.getLightMinutes()))
                .awakeMinutes(nvl(agg.getAwakeMinutes()))
                .caloriesIn(nvl(agg.getCaloriesIn()))
                .caloriesOut(nvl(agg.getCaloriesOut()))
                .highlights(agg.getHighlights() != null ? agg.getHighlights() : java.util.List.of())
                .summary(agg.getSummary())
                .build();
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private double nvl(Double v) {
        return v == null ? 0.0 : v;
    }
}