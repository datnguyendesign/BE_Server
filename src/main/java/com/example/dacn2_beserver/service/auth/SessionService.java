package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.exception.InvalidTokenException;
import com.example.dacn2_beserver.model.auth.Session;
import com.example.dacn2_beserver.model.enums.DevicePlatform;
import com.example.dacn2_beserver.model.enums.SessionStatus;
import com.example.dacn2_beserver.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SessionService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final long REFRESH_TTL_SECONDS = 30L * 24 * 60 * 60; // 30 ngày

    private final SessionRepository sessionRepository;

    public SessionIssueResult createSession(String userId, String deviceId, DevicePlatform platform) {
        String refreshPlain = randomToken();
        String refreshHash = OtpCrypto.sha256(refreshPlain);

        Session s = Session.builder()
                .userId(userId)
                .deviceId(deviceId)
                .platform(platform)
                .refreshTokenHash(refreshHash)
                .status(SessionStatus.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(REFRESH_TTL_SECONDS))
                .lastSeenAt(Instant.now())
                .build();

        s = sessionRepository.save(s);
        return new SessionIssueResult(s, refreshPlain);
    }

    /**
     * refresh rotation: token cũ -> rotate token mới
     */
    public SessionIssueResult rotate(String refreshTokenPlain) {
        String hash = OtpCrypto.sha256(refreshTokenPlain);
        Instant now = Instant.now();

        Session s = sessionRepository
                .findByRefreshTokenHashAndStatusAndExpiresAtAfter(hash, SessionStatus.ACTIVE, now)
                .orElseThrow(InvalidTokenException::new);

        String newPlain = randomToken();
        s.setRefreshTokenHash(OtpCrypto.sha256(newPlain));
        s.setLastSeenAt(now);

        // optional: gia hạn 30 ngày mỗi lần refresh
        s.setExpiresAt(now.plusSeconds(REFRESH_TTL_SECONDS));

        s = sessionRepository.save(s);
        return new SessionIssueResult(s, newPlain);
    }

    public void revoke(String userId, String sessionId, String reason) {
        Session s = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(InvalidTokenException::new);

        if (s.getStatus() != SessionStatus.ACTIVE) return;

        s.setStatus(SessionStatus.REVOKED);
        s.setRevokedAt(Instant.now());
        s.setRevokedReason(reason);
        sessionRepository.save(s);
    }

    /**
     * JWT gate: session phải ACTIVE + chưa hết hạn
     */
    public Session requireActive(String sessionId) {
        return sessionRepository
                .findByIdAndStatusAndExpiresAtAfter(sessionId, SessionStatus.ACTIVE, Instant.now())
                .orElseThrow(InvalidTokenException::new);
    }

    private String randomToken() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public record SessionIssueResult(Session session, String refreshTokenPlain) {
    }
}