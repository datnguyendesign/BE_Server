package com.example.dacn2_beserver.service.user;

import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserSettings;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    private User userWithEnabled(boolean enabled) {
        return User.builder().id("u1").username("dat")
                .settings(UserSettings.builder()
                        .notifications(NotificationSettings.builder().enabled(enabled).build())
                        .build())
                .build();
    }

    @Test
    void enableNotification() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithEnabled(false)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", true);

        assertThat(res.getSettings().getNotifications().getEnabled()).isTrue();
    }

    @Test
    void disableNotification() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithEnabled(true)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", false);

        assertThat(res.getSettings().getNotifications().getEnabled()).isFalse();
    }

    @Test
    void initializesNotificationsWhenNull() {
        User legacy = User.builder().id("u1").username("dat")
                .settings(UserSettings.builder().notifications(null).build())
                .build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(legacy));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", true);

        assertThat(res.getSettings().getNotifications().getEnabled()).isTrue();
    }

    @Test
    void initializesSettingsWhenNull() {
        User legacy = User.builder().id("u1").username("dat").build();
        legacy.setSettings(null);
        when(userRepository.findById("u1")).thenReturn(Optional.of(legacy));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", true);

        assertThat(res.getSettings().getNotifications().getEnabled()).isTrue();
    }

    @Test
    void unknownUserThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateNotificationEnabled("ghost", true))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void savesPersistedUser() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithEnabled(false)));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateNotificationEnabled("u1", true);

        assertThat(captor.getValue().getSettings().getNotifications().isEnabled()).isTrue();
    }
}
