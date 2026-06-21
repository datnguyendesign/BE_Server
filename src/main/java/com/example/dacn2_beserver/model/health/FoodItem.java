package com.example.dacn2_beserver.model.health;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("foods")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodItem {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code; // AI label, e.g. "apple_pie"

    @Indexed
    private String name;

    private List<String> tags;

    private Integer calories;

    // macros (grams)
    private Integer carbs;
    private Integer fat;
    private Integer protein;

    private Double servingSizeG;
    private String servingLabel;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}