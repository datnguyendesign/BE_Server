package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.health.SleepSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface SleepSessionRepository extends MongoRepository<SleepSession, String> {
    List<SleepSession> findAllByUserIdAndTimeStartAtBetweenOrderByTimeStartAtDesc(String userId, Instant from, Instant to);

    List<SleepSession> findTop50ByUserIdOrderByTimeStartAtDesc(String userId);
}