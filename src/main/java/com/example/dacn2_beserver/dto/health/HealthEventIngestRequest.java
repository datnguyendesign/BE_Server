package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.EventType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthEventIngestRequest {

    @NotNull
    private EventType type;

    private TimeRangeDto time;

    // payload linh hoạt
    private Map<String, Object> payload;

    private SourceMetaDto meta;
}