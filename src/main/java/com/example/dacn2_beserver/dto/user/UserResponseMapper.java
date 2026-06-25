package com.example.dacn2_beserver.dto.user;

import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.model.user.UserSettings;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Ánh xạ thuần User -> UserResponse, dùng chung cho /auth/me và /users/me/*. */
public final class UserResponseMapper {

    private UserResponseMapper() {
    }

    public static UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .primaryEmail(u.getPrimaryEmail())
                .profile(mapProfile(u.getProfile()))
                .settings(mapSettings(u.getSettings()))
                .goals(mapGoals(u.getGoals()))
                .status(u.getStatus())
                .roles(u.getRoles())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private static UserProfileDto mapProfile(UserProfile p) {
        if (p == null) {
            return null;
        }
        return UserProfileDto.builder()
                .fullName(p.getFullName())
                .avatarUrl(p.getAvatarUrl())
                .gender(p.getGender() == null ? null : p.getGender().name())
                .birthDate(formatBirthDate(p.getBirthday()))
                .heightCm(p.getHeightCm())
                .weightKg(p.getWeightKg())
                .bloodType(p.getBloodType())
                .conditions(p.getConditions())
                .build();
    }

    private static String formatBirthDate(Date birthday) {
        if (birthday == null) {
            return null;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return fmt.format(birthday);
    }

    private static UserSettingsDto mapSettings(UserSettings s) {
        if (s == null) {
            return null;
        }
        return UserSettingsDto.builder()
                .unitSystem(s.getUnitSystem())
                .language(s.getLanguage())
                .timezone(s.getTimezone())
                .notifications(mapNotifications(s.getNotifications()))
                .build();
    }

    private static NotificationSettingsDto mapNotifications(NotificationSettings n) {
        if (n == null) {
            return null;
        }
        return NotificationSettingsDto.builder()
                .enabled(n.isEnabled())
                .remindDrinkWater(n.isRemindDrinkWater())
                .remindSleep(n.isRemindSleep())
                .remindWorkout(n.isRemindWorkout())
                .build();
    }

    private static UserGoalsDto mapGoals(UserGoals g) {
        if (g == null) {
            return null;
        }
        return UserGoalsDto.builder()
                .dailySteps(g.getDailySteps())
                .dailyCaloriesIn(g.getDailyCaloriesIn())
                .dailyCaloriesOut(g.getDailyCaloriesOut())
                .dailyWaterMl(g.getDailyWaterMl())
                .targetWeightKg(g.getTargetWeightKg())
                .build();
    }
}
