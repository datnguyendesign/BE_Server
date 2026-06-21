package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("food_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private Instant loggedAt;

    // AI output
    private String label;      // e.g. "apple_pie"
    private Double confidence; // 0..1

    // Link to master data
    private String foodItemId;

    // snapshot nutrition (để lịch sử không bị đổi nếu FoodItem update)
    private Integer kcal;
    private Integer carbs;
    private Integer fat;
    private Integer protein;

    private RecordStatus status;
    private SourceMeta meta;

    @Builder.Default
    private Instant createdAt = Instant.now();
}