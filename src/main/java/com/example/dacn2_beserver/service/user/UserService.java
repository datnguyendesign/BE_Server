package com.example.dacn2_beserver.service.user;

import com.example.dacn2_beserver.dto.user.UpdateProfileRequest;
import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.dto.user.UserResponseMapper;
import com.example.dacn2_beserver.model.enums.Gender;
import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.model.user.UserSettings;
import com.example.dacn2_beserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse updateProfile(String userId, UpdateProfileRequest req) {
        User u = userRepository.findById(userId).orElseThrow();

        UserProfile p = u.getProfile() == null ? UserProfile.builder().build() : u.getProfile();

        // AboutYou: gender/birthDate/height/weight
        if (req.getGender() != null) {
            p.setGender(Gender.valueOf(req.getGender().toUpperCase()));
        }
        if (req.getBirthDate() != null) {
            p.setBirthday(req.getBirthDate());
        }
        if (req.getHeightCm() != null) p.setHeightCm(req.getHeightCm());
        if (req.getWeightKg() != null) p.setWeightKg(req.getWeightKg());

        // (optional) fullName/avatarUrl nếu FE cho chỉnh
        if (req.getFullName() != null) p.setFullName(req.getFullName());
        if (req.getAvatarUrl() != null) p.setAvatarUrl(req.getAvatarUrl());

        u.setProfile(p);
        u.setUpdatedAt(Instant.now());
        u = userRepository.save(u);

        return UserResponseMapper.toResponse(u);
    }

    public UserResponse updateNotificationEnabled(String userId, boolean enabled) {
        User u = userRepository.findById(userId).orElseThrow();

        UserSettings settings = u.getSettings() == null ? UserSettings.builder().build() : u.getSettings();
        NotificationSettings notifications = settings.getNotifications() == null
                ? NotificationSettings.builder().build()
                : settings.getNotifications();

        notifications.setEnabled(enabled);
        settings.setNotifications(notifications);
        u.setSettings(settings);
        u.setUpdatedAt(Instant.now());
        u = userRepository.save(u);

        return UserResponseMapper.toResponse(u);
    }
}