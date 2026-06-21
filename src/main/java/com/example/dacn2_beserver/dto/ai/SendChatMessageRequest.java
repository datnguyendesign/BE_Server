package com.example.dacn2_beserver.dto.ai;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendChatMessageRequest {

    private String content;

    private String imageObjectKey;

    // optional metadata
    private Map<String, Object> meta;
}