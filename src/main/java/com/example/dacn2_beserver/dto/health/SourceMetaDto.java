package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.DataSource;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceMetaDto {
    private DataSource source;
    private String deviceId;
    private String idempotencyKey;
    private String rawRef;
}