package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DailyAggregateServiceSleepTest {

    private DailyAggregateRepository aggRepo;
    private UserRepository userRepo;
    private DailyAggregateService service;

    @BeforeEach
    void setUp() {
        aggRepo = mock(DailyAggregateRepository.class);
        userRepo = mock(UserRepository.class);
        HealthEventRawRepository rawRepo = mock(HealthEventRawRepository.class);
        service = new DailyAggregateService(aggRepo, userRepo, rawRepo, new SleepScorer());

        when(userRepo.findById(anyString())).thenReturn(Optional.of(new User()));
        when(aggRepo.findByUserIdAndDate(anyString(), any())).thenReturn(Optional.empty());
        when(aggRepo.save(any(DailyAggregate.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void replaceModeSetsRatherThanAdds() {
        Instant wake = Instant.parse("2026-06-26T07:00:00Z");
        // first log 480 with replace
        DailyAggregate first = service.addSleep("u1", wake, 480, 60, 100, 300, 20, true);
        assertThat(first.getSleepMinutes()).isEqualTo(480);

        // existing day now returns 480; a second replace of 300 should SET to 300, not 780
        when(aggRepo.findByUserIdAndDate(anyString(), any())).thenReturn(Optional.of(first));
        DailyAggregate second = service.addSleep("u1", wake, 300, 40, 60, 190, 10, true);
        assertThat(second.getSleepMinutes()).isEqualTo(300);
    }

    @Test
    void cumulativeModeStillAdds() {
        Instant wake = Instant.parse("2026-06-26T07:00:00Z");
        DailyAggregate first = service.addSleep("u1", wake, 100, 0, 0, 100, 0, false);
        when(aggRepo.findByUserIdAndDate(anyString(), any())).thenReturn(Optional.of(first));
        DailyAggregate second = service.addSleep("u1", wake, 50, 0, 0, 50, 0, false);
        assertThat(second.getSleepMinutes()).isEqualTo(150);
    }

    @Test
    void sleepScoreIsComputed() {
        Instant wake = Instant.parse("2026-06-26T07:00:00Z");
        DailyAggregate agg = service.addSleep("u1", wake, 480, 60, 100, 300, 20, true);
        assertThat(agg.getSleepScore()).isEqualTo(100); // 8h -> full
    }
}
