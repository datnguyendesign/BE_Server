package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.enums.RecordStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("water_logs")
@CompoundIndex(name = "idx_user_time", def = "{'userId': 1, 'loggedAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private Instant loggedAt;

    private Integer amountMl;

    @Builder.Default
    private RecordStatus status = RecordStatus.CONFIRMED;

    private SourceMeta meta;

    @Builder.Default
    private Instant createdAt = Instant.now();
}