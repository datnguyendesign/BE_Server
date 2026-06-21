package com.example.dacn2_beserver.model.device;

import com.example.dacn2_beserver.model.enums.DevicePlatform;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("devices")
@CompoundIndex(name = "uq_user_device", def = "{'userId': 1, 'deviceId': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    private String id;

    private String userId;
    private String deviceId;

    private DevicePlatform platform;

    private String deviceName;
    private String appVersion;
    private String osVersion;

    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant lastSeenAt = Instant.now();
}