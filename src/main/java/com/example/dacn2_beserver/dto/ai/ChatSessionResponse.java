package com.example.dacn2_beserver.dto.ai;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {
    private String id;
    private String userId;
    private String title;
    private Map<String, Object> contextSnapshot;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
}