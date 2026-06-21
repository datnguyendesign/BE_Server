package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.enums.MealType;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("meals")
@CompoundIndex(name = "idx_user_time", def = "{'userId': 1, 'loggedAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    private MealType mealType;

    @Indexed
    private Instant loggedAt;

    private List<MealItem> items;

    private Integer totalCalories;

    private Double confidence; // AI infer
    private String imageRef;

    @Builder.Default
    private RecordStatus status = RecordStatus.CONFIRMED;

    private SourceMeta meta;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealItem {
        private String foodId;
        private String name;
        private Double amount;
        private String unit;
        private Integer calories;
        private Double proteinG;
        private Double fatG;
        private Double carbsG;
    }
}