package com.example.dacn2_beserver.model.ai;

import com.example.dacn2_beserver.model.enums.RecommendationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("recommendation_logs")
@CompoundIndex(name = "idx_user_time", def = "{'userId': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    private RecommendationType type;

    private Map<String, Object> inputs;
    private List<Map<String, Object>> candidates;
    private Map<String, Object> chosen;
    private Map<String, Object> feedback;

    @Builder.Default
    private Instant createdAt = Instant.now();
}