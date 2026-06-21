package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.dto.auth.*;
import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.exception.*;
import com.example.dacn2_beserver.model.auth.UserCredential;
import com.example.dacn2_beserver.model.auth.UserIdentity;
import com.example.dacn2_beserver.model.enums.DevicePlatform;
import com.example.dacn2_beserver.model.enums.IdentityProvider;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.UserCredentialRepository;
import com.example.dacn2_beserver.repository.UserIdentityRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import com.example.dacn2_beserver.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordAuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserCredentialRepository userCredentialRepository;

    private final SessionService sessionService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthResultResponse register(PasswordRegisterRequest req) {
        var resolved = resolve(req.getIdentifier());

        // đã tồn tại user theo email/phone -> conflict
        if (userIdentityRepository.existsByProviderAndNormalized(resolved.provider, resolved.normalized)) {
            if (resolved.provider == IdentityProvider.EMAIL) {
                throw new EmailAlreadyExistsException("Email already exists");
            }
            throw new PhoneAlreadyExistsException("Phone number already exists");
        }

        validatePassword(req.getPassword());

        // create user
        User u = User.builder().build();
        if (resolved.provider == IdentityProvider.EMAIL) {
            u.setPrimaryEmail(resolved.normalized);
        }
        u = userRepository.save(u);

        // create identity
        UserIdentity identity = UserIdentity.builder()
                .userId(u.getId())
                .provider(resolved.provider)
                .identifier(resolved.raw)
                .normalized(resolved.normalized)
                .verified(true)
                .build();
        userIdentityRepository.save(identity);

        // create credential
        UserCredential cred = UserCredential.builder()
                .userId(u.getId())
                .type("password")
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .passwordUpdatedAt(Instant.now())
                .build();
        userCredentialRepository.save(cred);

        return issueAuth(u, req.getDeviceId());
    }

    public AuthResultResponse login(PasswordLoginRequest req) {
        var resolved = resolve(req.getIdentifier());

        // tránh leak user existence: dùng INVALID_CREDENTIALS
        UserIdentity identity = userIdentityRepository
                .findByProviderAndNormalized(resolved.provider, resolved.normalized)
                .orElseThrow(() -> new InvalidCredentialsException());

        UserCredential cred = userCredentialRepository.findByUserId(identity.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (cred.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), cred.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        User u = userRepository.findById(identity.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return issueAuth(u, req.getDeviceId());
    }

    /**
     * Cho user đã login (OTP/Google) set password lần đầu hoặc update password
     */
    public void setPassword(AuthPrincipal principal, SetPasswordRequest req) {
        validatePassword(req.getPassword());

        UserCredential cred = userCredentialRepository.findByUserId(principal.userId())
                .orElseGet(() -> UserCredential.builder()
                        .userId(principal.userId())
                        .type("password")
                        .build());

        cred.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        cred.setPasswordUpdatedAt(Instant.now());
        cred.setUpdatedAt(Instant.now());
        userCredentialRepository.save(cred);
    }

    private AuthResultResponse issueAuth(User u, String deviceId) {
        var issued = sessionService.createSession(u.getId(), deviceId, DevicePlatform.WEB);
        String access = jwtService.generateAccessToken(u.getId(), issued.session().getId());

        return AuthResultResponse.builder()
                .user(UserResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .primaryEmail(u.getPrimaryEmail())
                        .status(u.getStatus())
                        .roles(u.getRoles())
                        .lastLoginAt(u.getLastLoginAt())
                        .createdAt(u.getCreatedAt())
                        .updatedAt(u.getUpdatedAt())
                        .build())
                .tokens(AuthTokensResponse.builder()
                        .accessToken(access)
                        .refreshToken(issued.refreshTokenPlain())
                        .expiresInSeconds(jwtService.getExpirationSeconds())
                        .build())
                .linkRequired(false)
                .build();
    }

    private void validatePassword(String password) {
        // policy tối thiểu: >= 8 ký tự
        if (password == null || password.trim().length() < 8) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Password must be at least 8 characters");
        }
    }

    private Resolved resolve(String identifier) {
        if (identifier == null) throw new ApiException(ErrorCode.VALIDATION_ERROR, "identifier required");

        String raw = identifier.trim();
        if (raw.contains("@")) {
            return new Resolved(IdentityProvider.EMAIL, raw, IdentifierNormalizer.normalizeEmail(raw));
        }
        return new Resolved(IdentityProvider.PHONE, raw, IdentifierNormalizer.normalizePhone(raw));
    }

    private record Resolved(IdentityProvider provider, String raw, String normalized) {
    }
}