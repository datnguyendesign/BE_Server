package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.health.WaterLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface WaterLogRepository extends MongoRepository<WaterLog, String> {
    List<WaterLog> findAllByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(String userId, Instant from, Instant to);

    List<WaterLog> findTop100ByUserIdOrderByLoggedAtDesc(String userId);
}