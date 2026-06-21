package com.example.dacn2_beserver.model.common;

import com.example.dacn2_beserver.model.enums.DataSource;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceMeta {
    private DataSource source;     // MANUAL/SMARTWATCH/PHONE_SENSOR/AI_INFERRED
    private String deviceId;       // nếu có
    private String idempotencyKey; // chống gửi lại (client generate)
    private String rawRef;         // optional: ref tới file/raw payload nếu bạn lưu ngoài
}