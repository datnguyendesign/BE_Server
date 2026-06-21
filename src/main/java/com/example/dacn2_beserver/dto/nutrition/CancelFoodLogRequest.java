package com.example.dacn2_beserver.dto.nutrition;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelFoodLogRequest {
    private String reason;               // optional
    private List<String> candidateCodes; // optional
}