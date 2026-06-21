package com.example.dacn2_beserver.model.health;

import com.example.dacn2_beserver.model.common.SourceMeta;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("events_raw")
@CompoundIndex(name = "uq_user_idempotency",
        def = "{'userId': 1, 'meta.idempotencyKey': 1}", unique = true, sparse = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthEventRaw {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private EventType type;

    private TimeRange time;

    private Map<String, Object> payload;

    private SourceMeta meta;

    @Builder.Default
    private Instant createdAt = Instant.now();
}