package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateWaterLogRequest;
import com.example.dacn2_beserver.dto.health.SourceMetaDto;
import com.example.dacn2_beserver.dto.health.WaterLogResponse;
import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import com.example.dacn2_beserver.model.health.WaterLog;
import com.example.dacn2_beserver.repository.WaterLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WaterService {

    private final WaterLogRepository waterLogRepository;
    private final DailyAggregateService dailyAggregateService;

    public WaterLogResponse create(String userId, CreateWaterLogRequest req) {
        Instant loggedAt = req.getLoggedAt().truncatedTo(ChronoUnit.SECONDS);

        WaterLog log = WaterLog.builder()
                .userId(userId)
                .loggedAt(loggedAt)
                .amountMl(req.getAmountMl())
                .status(RecordStatus.CONFIRMED)
                .meta(toMeta(req.getMeta()))
                .createdAt(Instant.now())
                .build();

        log = waterLogRepository.save(log);

        // update summary aggregate
        dailyAggregateService.addWater(userId, loggedAt, req.getAmountMl());

        return toResponse(log);
    }

    public List<WaterLog> list(String userId, Instant from, Instant to) {
        return waterLogRepository.findAllByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(userId, from, to);
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

    private WaterLogResponse toResponse(WaterLog log) {
        return WaterLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .loggedAt(log.getLoggedAt())
                .amountMl(log.getAmountMl())
                .status(log.getStatus())
                .meta(log.getMeta() == null ? null : SourceMetaDto.builder()
                        .source(log.getMeta().getSource())
                        .deviceId(log.getMeta().getDeviceId())
                        .idempotencyKey(log.getMeta().getIdempotencyKey())
                        .rawRef(log.getMeta().getRawRef())
                        .build())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public List<WaterLogResponse> listResponses(String userId, Instant from, Instant to) {
        return waterLogRepository
                .findAllByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(userId, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }
}