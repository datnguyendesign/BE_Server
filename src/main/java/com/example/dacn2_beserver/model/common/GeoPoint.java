package com.example.dacn2_beserver.model.common;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {
    private Double lat;
    private Double lng;
}