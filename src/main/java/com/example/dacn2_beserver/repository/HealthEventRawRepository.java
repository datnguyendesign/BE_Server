package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface HealthEventRawRepository extends MongoRepository<HealthEventRaw, String> {
    List<HealthEventRaw> findAllByUserIdAndTypeAndCreatedAtBetween(
            String userId, EventType type, Instant from, Instant to
    );

    List<HealthEventRaw> findAllByUserIdAndCreatedAtBetween(String userId, Instant from, Instant to);
}