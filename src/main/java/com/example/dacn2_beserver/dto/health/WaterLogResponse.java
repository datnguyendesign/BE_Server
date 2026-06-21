package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.RecordStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLogResponse {
    private String id;
    private String userId;
    private Instant loggedAt;
    private Integer amountMl;
    private RecordStatus status;
    private SourceMetaDto meta;
    private Instant createdAt;
}