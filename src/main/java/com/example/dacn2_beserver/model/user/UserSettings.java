package com.example.dacn2_beserver.model.user;

import com.example.dacn2_beserver.model.enums.UnitSystem;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {
    @Builder.Default
    private UnitSystem unitSystem = UnitSystem.METRIC;
    @Builder.Default
    private String language = "vi";
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    @Builder.Default
    private NotificationSettings notifications = NotificationSettings.builder().build();
}