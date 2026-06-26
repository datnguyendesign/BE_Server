package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateSleepSessionRequest;
import com.example.dacn2_beserver.dto.health.SleepSessionResponse;
import com.example.dacn2_beserver.dto.health.TimeRangeDto;
import com.example.dacn2_beserver.model.health.SleepSession;
import com.example.dacn2_beserver.repository.SleepSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SleepServiceTest {

    private SleepSessionRepository repo;
    private DailyAggregateService aggService;
    private SleepService service;

    @BeforeEach
    void setUp() {
        repo = mock(SleepSessionRepository.class);
        aggService = mock(DailyAggregateService.class);
        service = new SleepService(repo, aggService, new SleepStageEstimator());
        when(repo.save(any(SleepSession.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateSleepSessionRequest req(Instant start, Instant end) {
        return CreateSleepSessionRequest.builder()
                .time(TimeRangeDto.builder().startAt(start).endAt(end).build())
                .build();
    }

    @Test
    void manualLogEstimatesStagesAndReplaces() {
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant start = end.minus(8, ChronoUnit.HOURS); // 480 min
        SleepSessionResponse res = service.create("u1", req(start, end));

        assertThat(res.getTotalMinutes()).isEqualTo(480);
        assertThat(res.getDeepMinutes() + res.getRemMinutes()
                + res.getLightMinutes() + res.getAwakeMinutes()).isEqualTo(480);
        verify(aggService).addSleep(eq("u1"), eq(end), eq(480),
                anyInt(), anyInt(), anyInt(), anyInt(), eq(true));
    }

    @Test
    void futureEndRejected() {
        Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant start = end.minus(8, ChronoUnit.HOURS);
        assertThatThrownBy(() -> service.create("u1", req(start, end)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
