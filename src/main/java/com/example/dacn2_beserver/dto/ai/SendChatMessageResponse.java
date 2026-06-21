package com.example.dacn2_beserver.dto.ai;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendChatMessageResponse {
    private ChatMessageResponse userMessage;
    private ChatMessageResponse assistantMessage;
}