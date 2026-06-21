package com.example.dacn2_beserver.dto.ai;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatSessionRequest {
    private String title;
    private Map<String, Object> contextSnapshot;
}