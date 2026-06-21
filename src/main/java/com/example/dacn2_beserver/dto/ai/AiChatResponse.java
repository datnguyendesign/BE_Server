package com.example.dacn2_beserver.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatResponse {

    @JsonAlias({"content", "text", "answer", "message"})
    private String content;

    @JsonAlias({"suggested_actions", "suggestedActions", "actions"})
    private List<String> suggestedActions;

    // giữ raw data nếu AI trả thêm fields
    private Map<String, Object> meta;
}