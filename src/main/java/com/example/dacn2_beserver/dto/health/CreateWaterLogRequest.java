package com.example.dacn2_beserver.dto.health;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWaterLogRequest {
    @NotNull
    private Instant loggedAt;
    
    @NotNull
    @Min(1)
    private Integer amountMl;
    private SourceMetaDto meta;
}