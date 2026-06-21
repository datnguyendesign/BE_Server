package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.user.UpdateProfileRequest;
import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // FE AboutYou gọi endpoint này
    @PutMapping("/me/profile")
    public UserResponse updateMyProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req
    ) {
        return userService.updateProfile(principal.userId(), req);
    }
}