package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserSettings;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyAggregateServiceTest {

    @Mock DailyAggregateRepository dailyAggregateRepository;
    @Mock UserRepository userRepository;
    @Mock HealthEventRawRepository healthEventRawRepository;

    @InjectMocks DailyAggregateService service;

    private User utcUser() {
        return User.builder().id("u1").username("dat")
                .settings(UserSettings.builder().timezone("UTC").build())
                .build();
    }

    private HealthEventRaw reading(int bpm, Instant at) {
        return HealthEventRaw.builder()
                .userId("u1").type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(at).endAt(at).build())
                .payload(Map.of("bpm", bpm))
                .createdAt(at)
                .build();
    }

    @Test
    void singleReadingSetsAllThreeEqual() {
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(utcUser()));
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(reading(72, at)));
        when(dailyAggregateRepository.save(any(DailyAggregate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyAggregate agg = service.addHeartRate("u1", at, 72);

        assertThat(agg.getAvgHeartRate()).isEqualTo(72);
        assertThat(agg.getMaxHeartRate()).isEqualTo(72);
        assertThat(agg.getMinHeartRate()).isEqualTo(72);
    }

    @Test
    void multipleReadingsComputeAvgMaxMin() {
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(utcUser()));
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(reading(60, at), reading(80, at), reading(100, at)));
        when(dailyAggregateRepository.save(any(DailyAggregate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyAggregate agg = service.addHeartRate("u1", at, 100);

        assertThat(agg.getAvgHeartRate()).isEqualTo(80);
        assertThat(agg.getMaxHeartRate()).isEqualTo(100);
        assertThat(agg.getMinHeartRate()).isEqualTo(60);
    }

    @Test
    void recomputeIsIdempotentForSameReadings() {
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(utcUser()));
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(reading(70, at), reading(90, at)));
        when(dailyAggregateRepository.save(any(DailyAggregate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyAggregate first = service.addHeartRate("u1", at, 90);
        DailyAggregate second = service.addHeartRate("u1", at, 90);

        assertThat(first.getAvgHeartRate()).isEqualTo(80);
        assertThat(second.getAvgHeartRate()).isEqualTo(80);
        assertThat(second.getMaxHeartRate()).isEqualTo(90);
        assertThat(second.getMinHeartRate()).isEqualTo(70);
    }
}
