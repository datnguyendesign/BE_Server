package com.example.dacn2_beserver.dto.ai;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChatSessionRequest {
    private String title;
}