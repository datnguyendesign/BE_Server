package com.example.dacn2_beserver.model.ai;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("chat_sessions")
@CompoundIndex(name = "idx_user_updated", def = "{'userId': 1, 'updatedAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;

    private Map<String, Object> contextSnapshot;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant archivedAt;

    // Soft delete marker
    private Instant deletedAt;
}