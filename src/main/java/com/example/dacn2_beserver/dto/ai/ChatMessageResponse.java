package com.example.dacn2_beserver.dto.ai;

import com.example.dacn2_beserver.model.enums.ChatRole;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String id;
    private String sessionId;
    private String userId;
    private ChatRole role;
    private String content;

    private List<String> suggestedActions;

    private Map<String, Object> meta;
    private Instant createdAt;
}