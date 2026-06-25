package com.example.dacn2_beserver.service.user;

import com.example.dacn2_beserver.dto.user.UpdateProfileRequest;
import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.dto.user.UserResponseMapper;
import com.example.dacn2_beserver.model.enums.Gender;
import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.model.user.UserSettings;
import com.example.dacn2_beserver.exception.UserNotFoundException;
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
        Gender parsedGender = parseGender(req.getGender());
        if (parsedGender != null) {
            p.setGender(parsedGender);
        }
        if (req.getBirthDate() != null) {
            p.setBirthday(req.getBirthDate());
        }
        if (req.getHeightCm() != null) p.setHeightCm(req.getHeightCm());
        if (req.getWeightKg() != null) p.setWeightKg(req.getWeightKg());
        if (req.getBloodType() != null) p.setBloodType(req.getBloodType());
        if (req.getConditions() != null) p.setConditions(req.getConditions());

        // (optional) fullName/avatarUrl nếu FE cho chỉnh
        if (req.getFullName() != null) p.setFullName(req.getFullName());
        if (req.getAvatarUrl() != null) p.setAvatarUrl(req.getAvatarUrl());

        u.setProfile(p);
        u.setUpdatedAt(Instant.now());
        u = userRepository.save(u);

        return UserResponseMapper.toResponse(u);
    }

    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public UserResponse updateNotificationEnabled(String userId, boolean enabled) {
        User u = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

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