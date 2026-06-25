package com.example.dacn2_beserver.dto.health;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHeartRateRequest {

    @NotNull
    @Min(30)
    @Max(230)
    private Integer bpm;

    /** Thời điểm đo; null -> now ở service. */
    private Instant measuredAt;
}
