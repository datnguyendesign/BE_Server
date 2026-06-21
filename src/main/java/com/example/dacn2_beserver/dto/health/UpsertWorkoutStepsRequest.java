package com.example.dacn2_beserver.dto.health;

import jakarta.validation.constraints.Min;
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
public class UpsertWorkoutStepsRequest {

    @NotNull
    @Min(0)
    private Integer stepsTotal;

    private Long tsMs; // optional
}
