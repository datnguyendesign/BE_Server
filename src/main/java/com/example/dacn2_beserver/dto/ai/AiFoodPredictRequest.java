package com.example.dacn2_beserver.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFoodPredictRequest {
    @JsonProperty("image_url")
    private String imageUrl;
}