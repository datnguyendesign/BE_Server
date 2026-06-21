package com.example.dacn2_beserver.dto.user;

import com.example.dacn2_beserver.model.enums.UnitSystem;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDto {
    private UnitSystem unitSystem;
    private String language;
    private String timezone;

    private NotificationSettingsDto notifications;
}