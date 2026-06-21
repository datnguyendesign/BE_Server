package com.example.dacn2_beserver.model.ai;

import com.example.dacn2_beserver.model.enums.ChatRole;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("chat_messages")
@CompoundIndex(name = "idx_session_time", def = "{'sessionId': 1, 'createdAt': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    private String id;

    @Indexed
    private String sessionId;

    @Indexed
    private String userId;

    private ChatRole role;

    private String content;

    private Map<String, Object> meta;

    @Indexed(expireAfter = "180d")
    @Builder.Default
    private Instant createdAt = Instant.now();
}