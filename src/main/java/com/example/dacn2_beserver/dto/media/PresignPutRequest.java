package com.example.dacn2_beserver.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PresignPutRequest {
    private String contentType;
    private Long sizeBytes;
}