package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateHeartRateRequest;
import com.example.dacn2_beserver.dto.health.HeartRateReadingResponse;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartRateServiceTest {

    @Mock HealthEventRawRepository healthEventRawRepository;
    @Mock DailyAggregateService dailyAggregateService;

    HeartRateService service;

    private HeartRateService svc() {
        return new HeartRateService(healthEventRawRepository, dailyAggregateService);
    }

    @Test
    void createSavesRawAndAggregatesAndReturnsBpm() {
        service = svc();
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(healthEventRawRepository.save(any(HealthEventRaw.class)))
                .thenAnswer(inv -> {
                    HealthEventRaw r = inv.getArgument(0);
                    r.setId("r1");
                    return r;
                });

        CreateHeartRateRequest req = CreateHeartRateRequest.builder().bpm(75).measuredAt(at).build();
        HeartRateReadingResponse res = service.create("u1", req);

        // saved a HEART_RATE raw event with bpm in payload
        ArgumentCaptor<HealthEventRaw> captor = ArgumentCaptor.forClass(HealthEventRaw.class);
        verify(healthEventRawRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(EventType.HEART_RATE);
        assertThat(captor.getValue().getPayload().get("bpm")).isEqualTo(75);
        // rolled into aggregate
        verify(dailyAggregateService).addHeartRate(eq("u1"), eq(at), eq(75));
        // response carries bpm + measuredAt
        assertThat(res.getBpm()).isEqualTo(75);
        assertThat(res.getMeasuredAt()).isEqualTo(at);
        assertThat(res.getId()).isEqualTo("r1");
    }

    @Test
    void createDefaultsMeasuredAtToNowWhenNull() {
        service = svc();
        when(healthEventRawRepository.save(any(HealthEventRaw.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateHeartRateRequest req = CreateHeartRateRequest.builder().bpm(66).measuredAt(null).build();
        HeartRateReadingResponse res = service.create("u1", req);

        assertThat(res.getMeasuredAt()).isNotNull();
        assertThat(res.getMeasuredAt().getNano()).isZero();
        assertThat(res.getBpm()).isEqualTo(66);
    }

    @Test
    void listResponsesMapsBpmFromPayload() {
        service = svc();
        Instant a1 = Instant.parse("2026-06-26T08:00:00Z");
        Instant a2 = Instant.parse("2026-06-26T09:00:00Z");
        HealthEventRaw r1 = HealthEventRaw.builder().id("r1").userId("u1").type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(a2).endAt(a2).build())
                .payload(Map.of("bpm", 88)).createdAt(a2).build();
        HealthEventRaw r2 = HealthEventRaw.builder().id("r2").userId("u1").type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(a1).endAt(a1).build())
                .payload(Map.of("bpm", 70)).createdAt(a1).build();
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetweenOrderByTimeStartAtDesc(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(r1, r2));

        List<HeartRateReadingResponse> out = service.listResponses("u1", a1, a2);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getBpm()).isEqualTo(88);
        assertThat(out.get(0).getMeasuredAt()).isEqualTo(a2);
        assertThat(out.get(1).getBpm()).isEqualTo(70);
    }
}
