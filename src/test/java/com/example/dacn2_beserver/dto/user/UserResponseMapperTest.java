package com.example.dacn2_beserver.dto.user;

import com.example.dacn2_beserver.model.enums.Gender;
import com.example.dacn2_beserver.model.enums.UnitSystem;
import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.model.user.UserSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseMapperTest {

    @Test
    void mapsFullUser() {
        User u = User.builder()
                .id("u1").username("dat").primaryEmail("dat@example.com")
                .profile(UserProfile.builder()
                        .fullName("Dat Nguyen").avatarUrl("https://a/x.png")
                        .gender(Gender.MALE).heightCm(175.0).weightKg(68.0).build())
                .settings(UserSettings.builder()
                        .unitSystem(UnitSystem.METRIC).language("vi").timezone("Asia/Ho_Chi_Minh")
                        .notifications(NotificationSettings.builder()
                                .enabled(false).remindDrinkWater(true)
                                .remindSleep(true).remindWorkout(false).build())
                        .build())
                .goals(UserGoals.builder()
                        .dailySteps(9000).dailyCaloriesIn(2000).dailyCaloriesOut(500)
                        .dailyWaterMl(2000).targetWeightKg(65.0).build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getId()).isEqualTo("u1");
        assertThat(r.getUsername()).isEqualTo("dat");
        assertThat(r.getPrimaryEmail()).isEqualTo("dat@example.com");
        assertThat(r.getProfile().getFullName()).isEqualTo("Dat Nguyen");
        assertThat(r.getProfile().getAvatarUrl()).isEqualTo("https://a/x.png");
        assertThat(r.getProfile().getGender()).isEqualTo("MALE");
        assertThat(r.getProfile().getHeightCm()).isEqualTo(175.0);
        assertThat(r.getProfile().getWeightKg()).isEqualTo(68.0);
        assertThat(r.getSettings().getLanguage()).isEqualTo("vi");
        assertThat(r.getSettings().getUnitSystem()).isEqualTo(UnitSystem.METRIC);
        assertThat(r.getSettings().getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(r.getSettings().getNotifications().getEnabled()).isFalse();
        assertThat(r.getSettings().getNotifications().getRemindDrinkWater()).isTrue();
        assertThat(r.getSettings().getNotifications().getRemindSleep()).isTrue();
        assertThat(r.getSettings().getNotifications().getRemindWorkout()).isFalse();
        assertThat(r.getGoals().getDailySteps()).isEqualTo(9000);
        assertThat(r.getGoals().getDailyCaloriesIn()).isEqualTo(2000);
        assertThat(r.getGoals().getDailyCaloriesOut()).isEqualTo(500);
        assertThat(r.getGoals().getDailyWaterMl()).isEqualTo(2000);
        assertThat(r.getGoals().getTargetWeightKg()).isEqualTo(65.0);
    }

    @Test
    void nullProfileSettingsGoalsDoNotThrow() {
        User u = User.builder().id("u2").username("x").build();
        u.setProfile(null);
        u.setSettings(null);
        u.setGoals(null);

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getId()).isEqualTo("u2");
        assertThat(r.getProfile()).isNull();
        assertThat(r.getSettings()).isNull();
        assertThat(r.getGoals()).isNull();
    }

    @Test
    void nullGenderMapsToNull() {
        User u = User.builder().id("u3").username("y")
                .profile(UserProfile.builder().fullName("No Gender").build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getProfile().getGender()).isNull();
        assertThat(r.getProfile().getFullName()).isEqualTo("No Gender");
    }

    @Test
    void nullNotificationsDoNotThrow() {
        User u = User.builder().id("u4").username("z")
                .settings(UserSettings.builder()
                        .unitSystem(UnitSystem.METRIC).language("vi").timezone("Asia/Ho_Chi_Minh")
                        .notifications(null).build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getSettings()).isNotNull();
        assertThat(r.getSettings().getNotifications()).isNull();
    }

    @Test
    void mapsBirthDateBloodTypeAndConditions() throws Exception {
        java.util.Date dob = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2000-01-15");
        User u = User.builder().id("u5").username("dob")
                .profile(UserProfile.builder()
                        .fullName("Has DOB")
                        .birthday(dob)
                        .bloodType("O+")
                        .conditions(java.util.List.of("asthma", "penicillin allergy"))
                        .build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getProfile().getBirthDate()).isEqualTo("2000-01-15");
        assertThat(r.getProfile().getBloodType()).isEqualTo("O+");
        assertThat(r.getProfile().getConditions()).containsExactly("asthma", "penicillin allergy");
    }

    @Test
    void nullBirthdayMapsToNullBirthDate() {
        User u = User.builder().id("u6").username("nodob")
                .profile(UserProfile.builder().fullName("No DOB").build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getProfile().getBirthDate()).isNull();
        assertThat(r.getProfile().getBloodType()).isNull();
        assertThat(r.getProfile().getConditions()).isNull();
    }
}
