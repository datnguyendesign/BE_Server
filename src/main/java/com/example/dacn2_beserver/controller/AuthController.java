package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.auth.*;
import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.auth.AuthService;
import com.example.dacn2_beserver.service.auth.GoogleAuthService;
import com.example.dacn2_beserver.service.auth.OtpAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpAuthService otpAuthService;
    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/otp/request")
    public OtpRequestCreateResponse requestOtp(@Valid @RequestBody OtpRequestCreateRequest req) {
        return otpAuthService.requestOtp(req);
    }

    @PostMapping("/otp/verify")
    public OtpVerifyResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        return otpAuthService.verifyOtp(req);
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return authService.refresh(req.getRefreshToken()); // DTO đã có :contentReference[oaicite:12]{index=12}
    }

//    @PostMapping("/password/register")
//    public AuthResultResponse register(@Valid @RequestBody PasswordRegisterRequest req) {
//        return passwordAuthService.register(req);
//    }
//
//    @PostMapping("/password/login")
//    public AuthResultResponse login(@Valid @RequestBody PasswordLoginRequest req) {
//        return passwordAuthService.login(req);
//    }
//
//    @PostMapping("/password/set")
//    public void setPassword(@AuthenticationPrincipal AuthPrincipal principal,
//                            @Valid @RequestBody SetPasswordRequest req) {
//        passwordAuthService.setPassword(principal, req);
//    }

    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal);
    }

    @PostMapping("/google")
    public AuthResultResponse google(@Valid @RequestBody GoogleVerifyRequest req) {
        return googleAuthService.loginWithGoogle(req);
    }

    @PostMapping("/google/link/confirm")
    public AuthResultResponse confirmGoogleLink(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody LinkConfirmRequest req
    ) {
        return googleAuthService.confirmLink(principal, req);
    }

    @PostMapping("/google/link/reject")
    public AuthResultResponse rejectGoogleLink(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody LinkRejectRequest req
    ) {
        return googleAuthService.rejectLink(principal, req);
    }
}