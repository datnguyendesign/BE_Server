package com.example.dacn2_beserver.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFoodPredictResponse {

    private String status;

    @JsonProperty("is_food")
    private Boolean isFood;

    private String message;

    @Builder.Default
    private List<FoodPrediction> predictions = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FoodPrediction {
        private String label;
        private Double score;
    }
}