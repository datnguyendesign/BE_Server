package com.example.dacn2_beserver.dto.health;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushWorkoutPointsRequest {

    @NotNull
    @Valid
    private List<PointDto> points;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointDto {
        @NotNull
        private Long tsMs; // epoch millis

        @NotNull
        private Double lat;

        @NotNull
        private Double lng;

        private Double accuracyM;
        private Double speedMps;
    }
}
