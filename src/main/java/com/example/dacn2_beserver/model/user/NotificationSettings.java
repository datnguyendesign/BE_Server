package com.example.dacn2_beserver.model.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {
    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean remindDrinkWater = true;
    @Builder.Default
    private boolean remindSleep = true;
    @Builder.Default
    private boolean remindWorkout = false;
}