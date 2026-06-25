package com.example.dacn2_beserver.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationSettingsRequest {
    @NotNull
    private Boolean enabled;
}
