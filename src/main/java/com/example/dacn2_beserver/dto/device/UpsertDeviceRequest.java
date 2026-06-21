package com.example.dacn2_beserver.dto.device;

import com.example.dacn2_beserver.model.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertDeviceRequest {
    @NotBlank
    private String deviceId;
    @NotNull
    private DevicePlatform platform;

    private String deviceName;
    private String appVersion;
    private String osVersion;
}