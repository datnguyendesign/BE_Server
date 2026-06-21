package com.example.dacn2_beserver.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatRequest {

    @JsonProperty("session_id")
    private String sessionId;

    // text
    @JsonProperty("message")
    private String message;

    // optional image
    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("user_context")
    private Map<String, Object> userContext;
}