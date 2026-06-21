package com.example.dacn2_beserver.dto.nutrition;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NutritionAnalyzeResponse {

    private boolean isFood;
    private String message;

    private double thresholdUsed;

    private Candidate primaryCandidate;

    @Builder.Default
    private List<Candidate> candidates = new ArrayList<>();

    public enum CandidateStatus {
        KNOWN,
        UNKNOWN
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {
        private String code;   // AI label (matches FoodItem.code)
        private Double score;

        private CandidateStatus status; // KNOWN / UNKNOWN
        private FoodItemSnapshot foodItem; // nullable if UNKNOWN
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FoodItemSnapshot {
        private String id;
        private String code;
        private String name;

        private Integer calories;
        private Integer carbs;
        private Integer fat;
        private Integer protein;
    }
}