package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.health.MealLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface MealLogRepository extends MongoRepository<MealLog, String> {
    List<MealLog> findAllByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(String userId, Instant from, Instant to);

    List<MealLog> findTop50ByUserIdOrderByLoggedAtDesc(String userId);
}