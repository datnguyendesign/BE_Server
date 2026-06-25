package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateHeartRateRequest;
import com.example.dacn2_beserver.dto.health.HeartRateReadingResponse;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Lưu chỉ số nhịp tim (raw event) và cộng vào DailyAggregate.
 * Theo khuôn WaterService.
 */
@Service
@RequiredArgsConstructor
public class HeartRateService {

    private final HealthEventRawRepository healthEventRawRepository;
    private final DailyAggregateService dailyAggregateService;

    public HeartRateReadingResponse create(String userId, CreateHeartRateRequest req) {
        Instant measuredAt = (req.getMeasuredAt() != null ? req.getMeasuredAt() : Instant.now())
                .truncatedTo(ChronoUnit.SECONDS);
        int bpm = req.getBpm();

        HealthEventRaw event = HealthEventRaw.builder()
                .userId(userId)
                .type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(measuredAt).endAt(measuredAt).build())
                .payload(Map.of("bpm", bpm))
                .createdAt(Instant.now())
                .build();

        event = healthEventRawRepository.save(event);

        dailyAggregateService.addHeartRate(userId, measuredAt, bpm);

        return toResponse(event);
    }

    public List<HeartRateReadingResponse> listResponses(String userId, Instant from, Instant to) {
        return healthEventRawRepository
                .findAllByUserIdAndTypeAndTimeStartAtBetweenOrderByTimeStartAtDesc(
                        userId, EventType.HEART_RATE, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private HeartRateReadingResponse toResponse(HealthEventRaw event) {
        int bpm = 0;
        if (event.getPayload() != null && event.getPayload().get("bpm") instanceof Number n) {
            bpm = n.intValue();
        }
        Instant measuredAt = event.getTime() != null ? event.getTime().getStartAt() : event.getCreatedAt();
        return HeartRateReadingResponse.builder()
                .id(event.getId())
                .bpm(bpm)
                .measuredAt(measuredAt)
                .build();
    }
}
