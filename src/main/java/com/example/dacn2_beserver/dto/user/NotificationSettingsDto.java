package com.example.dacn2_beserver.dto.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsDto {
    private Boolean enabled;
    private Boolean remindDrinkWater;
    private Boolean remindSleep;
    private Boolean remindWorkout;
}