package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.auth.Session;
import com.example.dacn2_beserver.model.enums.SessionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends MongoRepository<Session, String> {
    List<Session> findAllByUserIdAndStatus(String userId, SessionStatus status);

    Optional<Session> findByIdAndUserId(String id, String userId);

    Optional<Session> findByRefreshTokenHashAndStatusAndExpiresAtAfter(
            String refreshTokenHash,
            SessionStatus status,
            Instant now
    );

    Optional<Session> findByIdAndStatusAndExpiresAtAfter(
            String id,
            SessionStatus status,
            Instant now
    );

    List<Session> findAllByUserIdAndExpiresAtAfter(String userId, Instant now);

    void deleteAllByUserId(String userId);
}