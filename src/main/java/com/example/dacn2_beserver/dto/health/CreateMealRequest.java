package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.MealType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMealRequest {

    @NotNull
    private MealType mealType;

    @NotNull
    private Instant loggedAt;

    @NotNull
    private List<MealItemDto> items;

    private Double confidence;
    private String imageRef;
    private SourceMetaDto meta;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealItemDto {
        private String foodId;
        private String name;
        private Double amount;
        private String unit;
        private Integer calories;
    }
}