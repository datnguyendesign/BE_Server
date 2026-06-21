package com.example.dacn2_beserver.dto.nutrition;

import com.example.dacn2_beserver.model.enums.DataSource;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmFoodLogRequest {
    private String foodCode;        // required
    private Double confidence;      // optional (0..1)

    // Recommended: allow future reuse for manual logging, default AI_INFERRED if null
    private DataSource source;      // optional: MANUAL/SMARTWATCH/PHONE_SENSOR/AI_INFERRED

    // Recommended: prevent double-tap confirm (client-generated)
    private String idempotencyKey;  // optional

    // Optional trace reference (e.g. image objectKey, requestId, etc.)
    private String rawRef;          // optional
}