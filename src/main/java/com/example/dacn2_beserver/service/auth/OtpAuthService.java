package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.dto.auth.OtpRequestCreateRequest;
import com.example.dacn2_beserver.dto.auth.OtpRequestCreateResponse;
import com.example.dacn2_beserver.dto.auth.OtpVerifyRequest;
import com.example.dacn2_beserver.dto.auth.OtpVerifyResponse;
import com.example.dacn2_beserver.exception.*;
import com.example.dacn2_beserver.model.auth.OtpRequest;
import com.example.dacn2_beserver.model.auth.UserIdentity;
import com.example.dacn2_beserver.model.enums.*;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.OtpRequestRepository;
import com.example.dacn2_beserver.repository.UserIdentityRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpAuthService {
    private static final int MAX_ATTEMPTS = 5;
    private static final long OTP_TTL_SECONDS = 5 * 60;
    private static final long MIN_REQUEST_INTERVAL_SECONDS = 30; // chống spam basic

    private final OtpRequestRepository otpRequestRepository;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    private final OtpSender otpSender;
    private final SessionService sessionService;
    private final JwtService jwtService;

    public OtpRequestCreateResponse requestOtp(OtpRequestCreateRequest req) {
        OtpChannel channel = req.getChannel();
        String identifier = req.getIdentifier();

        // 1. Validate
        if (channel == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "OTP channel is required");
        }

        String normalized = switch (channel) {
            case EMAIL -> IdentifierNormalizer.normalizeEmail(identifier);
            case SMS -> IdentifierNormalizer.normalizePhone(identifier);
        };

        // 2.anti-spam: nếu OTP gần nhất < 30s thì chặn
        otpRequestRepository.findTopByIdentifierNormalizedAndPurposeOrderByCreatedAtDesc(
                normalized, OtpPurpose.LOGIN
        ).ifPresent(last -> {
            if (last.getCreatedAt() != null &&
                    last.getCreatedAt().isAfter(Instant.now().minusSeconds(MIN_REQUEST_INTERVAL_SECONDS))) {
                throw new OTPTooSoonException("OTP requested too soon");
            }
        });

        // 3. Generate OTP code + hash
        String code = OtpCrypto.generate6Digits();
        String codeHash = OtpCrypto.sha256(code);

        OtpRequest otp = OtpRequest.builder()
                .channel(channel)
                .purpose(OtpPurpose.LOGIN)
                .identifier(identifier)
                .identifierNormalized(normalized)
                .codeHash(codeHash)
                .status(OtpStatus.PENDING)
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(OTP_TTL_SECONDS))
                .build();

        otp = otpRequestRepository.save(otp);

        // 4. Send OTP
        otpSender.send(channel, identifier, code);

        // 5. Return otpRequestId cho FE
        return OtpRequestCreateResponse.builder()
                .otpRequestId(otp.getId())
                .expiresInSeconds(OTP_TTL_SECONDS)
                .build();
    }

    public OtpVerifyResponse verifyOtp(OtpVerifyRequest req) {
        String identifier = req.getIdentifier();
        Instant now = Instant.now();
        OtpRequest otp;

        // 1. Resolve OTP (otpRequestId > channel)
        if (req.getOtpRequestId() != null && !req.getOtpRequestId().isBlank()) {

            otp = otpRequestRepository.findById(req.getOtpRequestId())
                    .filter(o -> o.getStatus() == OtpStatus.PENDING)
                    .filter(o -> o.getExpiresAt() != null && o.getExpiresAt().isAfter(now))
                    .orElseThrow(() -> new ApiException(ErrorCode.OTP_EXPIRED));

        } else {
            // Không có otpRequestId thì BẮT BUỘC channel
            if (req.getChannel() == null) {
                throw new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        "Either otpRequestId or channel must be provided"
                );
            }

            String normalized = (req.getChannel() == OtpChannel.EMAIL)
                    ? IdentifierNormalizer.normalizeEmail(req.getIdentifier())
                    : IdentifierNormalizer.normalizePhone(req.getIdentifier());

            List<OtpRequest> candidates =
                    otpRequestRepository.findAllByIdentifierNormalizedAndPurposeAndStatusAndExpiresAtAfter(
                            normalized,
                            OtpPurpose.LOGIN,
                            OtpStatus.PENDING,
                            now
                    );

            otp = candidates.stream()
                    .max(Comparator.comparing(OtpRequest::getCreatedAt))
                    .orElseThrow(() -> new OTPExpiredException("OTP expired"));
        }

        // 2. Check code
        String codeHash = OtpCrypto.sha256(req.getCode());
        if (!codeHash.equals(otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= MAX_ATTEMPTS) {
                otp.setStatus(OtpStatus.LOCKED);
                otp.setLockedAt(now);
            }
            otpRequestRepository.save(otp);

            throw new OTPInvalidException("OTP code invalid");
        }

        // 3. verified
        otp.setStatus(OtpStatus.VERIFIED);
        otp.setVerifiedAt(now);
        otpRequestRepository.save(otp);

        // 4. login or register
        IdentityProvider provider = (otp.getChannel() == OtpChannel.EMAIL)
                ? IdentityProvider.EMAIL
                : IdentityProvider.PHONE;

        String normalized = otp.getIdentifierNormalized();

        // 1) tìm user theo identity
        var existing = userIdentityRepository.findByProviderAndNormalized(provider, normalized);
        boolean isNewUser = existing.isEmpty();

        String userId = existing
                .map(UserIdentity::getUserId)
                .orElseGet(() -> createUserAndIdentity(provider, otp.getIdentifier(), normalized));

        // 2) tạo session + refresh token (30 ngày)
        var platform = DevicePlatform.WEB; // tạm, sau này map theo header hoặc body
        var issued = sessionService.createSession(userId, req.getDeviceId(), platform);

        // 3) JWT access token
        String accessToken = jwtService.generateAccessToken(userId, issued.session().getId());

        boolean linkSuggested = false;
        String message = null;

        // Chỉ gợi ý khi OTP là EMAIL
        if (provider == IdentityProvider.EMAIL) {
            // user hiện tại đã có GOOGLE identity chưa?
            boolean userHasGoogle = userIdentityRepository.findAllByUserId(userId).stream()
                    .anyMatch(i -> i.getProvider() == IdentityProvider.GOOGLE);

            // hệ thống có google account nào từng dùng email này không?
            // NOTE: normalized đang là email normalize (lowercase)
            boolean emailHasGoogleAccount = userIdentityRepository
                    .existsByProviderAndEmailAtProvider(IdentityProvider.GOOGLE, normalized);

            if (!userHasGoogle && emailHasGoogleAccount) {
                linkSuggested = true;
                message = "Email này đang có Google account, hãy bấm Continue with Google để link.";
            }
        }

        return OtpVerifyResponse.builder()
                .userId(userId)
                .sessionId(issued.session().getId())
                .accessToken(accessToken)
                .refreshToken(issued.refreshTokenPlain())
                .expiresInSeconds(jwtService.getExpirationSeconds())
                .linkSuggested(linkSuggested)
                .message(message)
                .isNewUser(isNewUser)
                .displayIdentifier(otp.getIdentifier()) // raw email/phone FE show
                .build();
    }

    private OtpRequest pickLatest(List<OtpRequest> a, List<OtpRequest> b) {
        OtpRequest best = null;
        for (OtpRequest x : a) best = newer(best, x);
        for (OtpRequest x : b) best = newer(best, x);
        return best;
    }

    private OtpRequest newer(OtpRequest cur, OtpRequest next) {
        if (cur == null) return next;
        if (next == null) return cur;
        if (cur.getCreatedAt() == null) return next;
        if (next.getCreatedAt() == null) return cur;
        return next.getCreatedAt().isAfter(cur.getCreatedAt()) ? next : cur;
    }

    private String createUserAndIdentity(IdentityProvider provider, String identifierRaw, String normalized) {
        User u = User.builder().build();

        if (provider == IdentityProvider.EMAIL) {
            u.setPrimaryEmail(IdentifierNormalizer.normalizeEmail(identifierRaw));
        }
        // PHONE: primaryEmail để null, vẫn ok vì field sparse unique :contentReference[oaicite:13]{index=13}

        u = userRepository.save(u);

        UserIdentity identity = UserIdentity.builder()
                .userId(u.getId())
                .provider(provider)
                .identifier(identifierRaw)
                .normalized(normalized)
                .verified(true)
                .build();

        userIdentityRepository.save(identity);
        return u.getId();
    }
}