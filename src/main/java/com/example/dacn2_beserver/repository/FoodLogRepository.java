package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.health.FoodLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FoodLogRepository extends MongoRepository<FoodLog, String> {
    List<FoodLog> findAllByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(String userId, Instant from, Instant to);

    Optional<FoodLog> findByIdAndUserId(String id, String userId);

    Optional<FoodLog> findFirstByUserIdAndMetaIdempotencyKey(String userId, String idempotencyKey);
}