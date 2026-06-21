package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateSleepSessionRequest;
import com.example.dacn2_beserver.dto.health.SleepSessionResponse;
import com.example.dacn2_beserver.dto.health.SourceMetaDto;
import com.example.dacn2_beserver.dto.health.TimeRangeDto;
import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import com.example.dacn2_beserver.model.enums.SleepStage;
import com.example.dacn2_beserver.model.health.SleepSession;
import com.example.dacn2_beserver.repository.SleepSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SleepService {

    private final SleepSessionRepository sleepSessionRepository;
    private final DailyAggregateService dailyAggregateService;

    public SleepSessionResponse create(String userId, CreateSleepSessionRequest req) {
        // 1) Validate time range
        Instant startAt = req.getTime().getStartAt();
        Instant endAt = req.getTime().getEndAt();
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Invalid sleep time range");
        }

        // 2) Compute total + stage minutes
        int totalMinutes = (int) Duration.between(startAt, endAt).toMinutes();
        Map<SleepStage, Integer> stageMinutes = computeStageMinutes(req.getSegments());

        int deep = stageMinutes.getOrDefault(SleepStage.DEEP, 0);
        int rem = stageMinutes.getOrDefault(SleepStage.REM, 0);
        int light = stageMinutes.getOrDefault(SleepStage.LIGHT, 0);
        int awake = stageMinutes.getOrDefault(SleepStage.AWAKE, 0);

        // 3) Build entity
        SleepSession session = SleepSession.builder()
                .userId(userId)
                .time(TimeRange.builder().startAt(startAt).endAt(endAt).build())
                .totalMinutes(totalMinutes)
                .deepMinutes(deep)
                .remMinutes(rem)
                .lightMinutes(light)
                .awakeMinutes(awake)
                .segments(toSegments(req.getSegments()))
                .status(RecordStatus.CONFIRMED)
                .meta(toMeta(req.getMeta()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        session = sleepSessionRepository.save(session);

        // 4) Update summary by "wake-up day" (endAt)
        dailyAggregateService.addSleep(userId, endAt, totalMinutes, deep, rem, light, awake);

        return toResponse(session);
    }

    public List<SleepSessionResponse> listResponses(String userId, Instant from, Instant to) {
        return sleepSessionRepository
                .findAllByUserIdAndTimeStartAtBetweenOrderByTimeStartAtDesc(userId, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Map<SleepStage, Integer> computeStageMinutes(List<CreateSleepSessionRequest.SleepSegmentDto> segments) {
        if (segments == null || segments.isEmpty()) return Collections.emptyMap();

        Map<SleepStage, Long> sums = new EnumMap<>(SleepStage.class);
        for (var seg : segments) {
            if (seg.getStage() == null || seg.getStartAt() == null || seg.getEndAt() == null) continue;
            if (!seg.getEndAt().isAfter(seg.getStartAt())) continue;

            SleepStage stage;
            try {
                stage = SleepStage.valueOf(seg.getStage().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                continue;
            }

            long mins = Duration.between(seg.getStartAt(), seg.getEndAt()).toMinutes();
            sums.merge(stage, mins, Long::sum);
        }

        Map<SleepStage, Integer> out = new EnumMap<>(SleepStage.class);
        for (var e : sums.entrySet()) out.put(e.getKey(), (int) Math.max(0, e.getValue()));
        return out;
    }

    private List<SleepSession.SleepSegment> toSegments(List<CreateSleepSessionRequest.SleepSegmentDto> segments) {
        if (segments == null) return null;
        List<SleepSession.SleepSegment> out = new ArrayList<>();
        for (var s : segments) {
            if (s.getStage() == null) continue;
            SleepStage stage;
            try {
                stage = SleepStage.valueOf(s.getStage().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                continue;
            }
            out.add(SleepSession.SleepSegment.builder()
                    .stage(stage)
                    .startAt(s.getStartAt())
                    .endAt(s.getEndAt())
                    .build());
        }
        return out;
    }

    private SourceMeta toMeta(SourceMetaDto dto) {
        if (dto == null) return null;
        return SourceMeta.builder()
                .source(dto.getSource())
                .deviceId(dto.getDeviceId())
                .idempotencyKey(dto.getIdempotencyKey())
                .rawRef(dto.getRawRef())
                .build();
    }

    private SleepSessionResponse toResponse(SleepSession s) {
        TimeRange tr = s.getTime();
        TimeRangeDto time = tr == null ? null : TimeRangeDto.builder()
                .startAt(tr.getStartAt())
                .endAt(tr.getEndAt())
                .build();

        List<SleepSessionResponse.SleepSegmentDto> segs = null;
        if (s.getSegments() != null) {
            segs = s.getSegments().stream().map(x -> SleepSessionResponse.SleepSegmentDto.builder()
                    .stage(x.getStage() == null ? null : x.getStage().name())
                    .startAt(x.getStartAt())
                    .endAt(x.getEndAt())
                    .build()).toList();
        }

        SourceMetaDto meta = s.getMeta() == null ? null : SourceMetaDto.builder()
                .source(s.getMeta().getSource())
                .deviceId(s.getMeta().getDeviceId())
                .idempotencyKey(s.getMeta().getIdempotencyKey())
                .rawRef(s.getMeta().getRawRef())
                .build();

        return SleepSessionResponse.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .time(time)
                .totalMinutes(s.getTotalMinutes())
                .deepMinutes(s.getDeepMinutes())
                .remMinutes(s.getRemMinutes())
                .lightMinutes(s.getLightMinutes())
                .awakeMinutes(s.getAwakeMinutes())
                .segments(segs)
                .status(s.getStatus())
                .meta(meta)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}