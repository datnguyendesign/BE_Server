package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.dto.auth.*;
import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.exception.GoogleAlreadyLinkedException;
import com.example.dacn2_beserver.exception.LinkTicketExpiredException;
import com.example.dacn2_beserver.exception.UnauthorizedException;
import com.example.dacn2_beserver.exception.UserNotFoundException;
import com.example.dacn2_beserver.model.auth.LinkTicket;
import com.example.dacn2_beserver.model.auth.UserIdentity;
import com.example.dacn2_beserver.model.enums.DevicePlatform;
import com.example.dacn2_beserver.model.enums.IdentityProvider;
import com.example.dacn2_beserver.model.enums.LinkTicketStatus;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.repository.LinkTicketRepository;
import com.example.dacn2_beserver.repository.UserIdentityRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import com.example.dacn2_beserver.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleIdTokenVerifierService verifier;
    private final UserIdentityRepository userIdentityRepository;
    private final UserRepository userRepository;
    private final LinkTicketRepository linkTicketRepository;

    private final SessionService sessionService;
    private final JwtService jwtService;

    public AuthResultResponse loginWithGoogle(GoogleVerifyRequest req) {
        var claims = verifier.verify(req.getIdToken());
        String sub = claims.getSub();
        String emailNorm = claims.getEmail() == null ? null : IdentifierNormalizer.normalizeEmail(claims.getEmail());

        // Case A: đã có GOOGLE(sub) -> login
        var existingGoogle = userIdentityRepository.findByProviderAndNormalized(IdentityProvider.GOOGLE, sub);
        if (existingGoogle.isPresent()) {
            return issueAuth(existingGoogle.get().getUserId(), req.getDeviceId(), false);
        }

        // Case B: chưa có sub, nhưng email match user khác -> tạo LinkTicket
        if (emailNorm != null) {
            var emailIdentity = userIdentityRepository.findByProviderAndNormalized(IdentityProvider.EMAIL, emailNorm);
            if (emailIdentity.isPresent()) {
                LinkTicket ticket = LinkTicket.builder()
                        .targetUserId(emailIdentity.get().getUserId())
                        .googleSub(sub)
                        .googleEmail(claims.getEmail())
                        .googleEmailVerified(claims.getEmailVerified())
                        .name(claims.getName())
                        .picture(claims.getPicture())
                        .status(LinkTicketStatus.PENDING)
                        .expiresAt(Instant.now().plusSeconds(10 * 60))
                        .build();

                ticket = linkTicketRepository.save(ticket);

                return AuthResultResponse.builder()
                        .linkRequired(true)
                        .linkTicketId(ticket.getId())
                        .message("Email already exists. Confirm to link Google account.")
                        .build();
            }
        }

        // Case C: không match ai -> create user + create GOOGLE identity (+ EMAIL identity nếu có email)
        User u = User.builder().build();
        if (emailNorm != null) u.setPrimaryEmail(emailNorm);

        u.setProfile(UserProfile.builder()
                .fullName(claims.getName())
                .avatarUrl(claims.getPicture())
                .build());

        u = userRepository.save(u);

        // GOOGLE identity
        UserIdentity googleIdentity = UserIdentity.builder()
                .userId(u.getId())
                .provider(IdentityProvider.GOOGLE)
                .identifier(sub)
                .normalized(sub)
                .verified(true)
                .providerAccountId(sub) // optional field, repo có sẵn :contentReference[oaicite:11]{index=11}
                .emailAtProvider(emailNorm)
                .emailVerifiedAtProvider(claims.getEmailVerified())
                .build();
        userIdentityRepository.save(googleIdentity);

        // Nếu Google trả email verified => tạo luôn EMAIL identity để OTP email về sau map đúng user này
        if (emailNorm != null && Boolean.TRUE.equals(claims.getEmailVerified())
                && !userIdentityRepository.existsByProviderAndNormalized(IdentityProvider.EMAIL, emailNorm)) {

            UserIdentity emailIdentity = UserIdentity.builder()
                    .userId(u.getId())
                    .provider(IdentityProvider.EMAIL)
                    .identifier(claims.getEmail())
                    .normalized(emailNorm)
                    .verified(true)
                    .build();
            userIdentityRepository.save(emailIdentity);
        }

        return issueAuth(u.getId(), req.getDeviceId(), true);
    }

    public AuthResultResponse confirmLink(AuthPrincipal principal, LinkConfirmRequest req) {
        Instant now = Instant.now();

        LinkTicket t = linkTicketRepository
                .findByIdAndStatusAndExpiresAtAfter(req.getLinkTicketId(), LinkTicketStatus.PENDING, now)
                .orElseThrow(() -> new LinkTicketExpiredException(req.getLinkTicketId() + " is expired or invalid"));

        if (!principal.userId().equals(t.getTargetUserId())) {
            throw new UnauthorizedException("You cannot confirm link ticket for another user");
        }

        // Nếu google sub đã linked đâu đó rồi -> conflict
        if (userIdentityRepository.existsByProviderAndNormalized(IdentityProvider.GOOGLE, t.getGoogleSub())) {
            throw new GoogleAlreadyLinkedException("Google account already linked to another user");
        }

        // Tạo GOOGLE identity cho targetUser
        UserIdentity googleIdentity = UserIdentity.builder()
                .userId(t.getTargetUserId())
                .provider(IdentityProvider.GOOGLE)
                .identifier(t.getGoogleSub())
                .normalized(t.getGoogleSub())
                .verified(true)
                .providerAccountId(t.getGoogleSub())
                .emailAtProvider(t.getGoogleEmail() == null ? null : IdentifierNormalizer.normalizeEmail(t.getGoogleEmail()))
                .emailVerifiedAtProvider(t.getGoogleEmailVerified())
                .build();

        userIdentityRepository.save(googleIdentity);

        // mark ticket confirmed (TTL sẽ xoá sau ~10 phút, hoặc bạn có thể delete luôn)
        t.setStatus(LinkTicketStatus.CONFIRMED);
        t.setConfirmedAt(now);
        linkTicketRepository.save(t);

        return issueAuth(t.getTargetUserId(), req.getDeviceId(), false);
    }

    private AuthResultResponse issueAuth(String userId, String deviceId, boolean isNewUser) {
        var platform = DevicePlatform.WEB; // Hoặc có thể detect từ deviceId nếu muốn
        var issued = sessionService.createSession(userId, deviceId, platform);

        String accessToken = jwtService.generateAccessToken(userId, issued.session().getId());

        User u = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return AuthResultResponse.builder()
                .user(toUserResponse(u))
                .tokens(AuthTokensResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(issued.refreshTokenPlain())
                        .expiresInSeconds(jwtService.getExpirationSeconds())
                        .build())
                .linkRequired(false)
                .isNewUser(isNewUser)
                .build();
    }

    private UserResponse toUserResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .primaryEmail(u.getPrimaryEmail())
                .profile(u.getProfile() == null ? null : com.example.dacn2_beserver.dto.user.UserProfileDto.builder()
                        .fullName(u.getProfile().getFullName())
                        .avatarUrl(u.getProfile().getAvatarUrl())
                        .gender(u.getProfile().getGender() == null ? null : u.getProfile().getGender().name())
                        // birthYear: nếu bạn muốn convert Date -> year ở đây
                        .heightCm(u.getProfile().getHeightCm())
                        .weightKg(u.getProfile().getWeightKg())
                        .build())
                .settings(null) // nếu muốn trả settings/goals, map tương tự
                .goals(null)
                .status(u.getStatus())
                .roles(u.getRoles())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    public AuthResultResponse rejectLink(AuthPrincipal principal, LinkRejectRequest req) {
        Instant now = Instant.now();

        LinkTicket t = linkTicketRepository
                .findByIdAndStatusAndExpiresAtAfter(req.getLinkTicketId(), LinkTicketStatus.PENDING, now)
                .orElseThrow(() -> new LinkTicketExpiredException(req.getLinkTicketId() + " is expired or invalid"));

        if (!principal.userId().equals(t.getTargetUserId())) {
            throw new UnauthorizedException("You cannot reject link ticket for another user");
        }

        t.setStatus(LinkTicketStatus.REJECTED);
        t.setRejectedAt(now);
        linkTicketRepository.save(t);

        return AuthResultResponse.builder()
                .linkRequired(false)
                .message("Link rejected")
                .build();
    }
}