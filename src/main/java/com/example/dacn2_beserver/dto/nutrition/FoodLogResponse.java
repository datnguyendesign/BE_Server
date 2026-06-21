package com.example.dacn2_beserver.dto.nutrition;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodLogResponse {

    private String id;
    private Instant loggedAt;

    private String label;       // same as foodCode
    private Double confidence;

    private String foodItemId;
    private String foodCode;
    private String foodName;

    private Integer kcal;
    private Integer carbs;
    private Integer fat;
    private Integer protein;

    private boolean addedToDailyAggregate; // true if kcal != null and delta applied
}