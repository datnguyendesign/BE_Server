package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.health.WorkoutSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface WorkoutSessionRepository extends MongoRepository<WorkoutSession, String> {
    List<WorkoutSession> findAllByUserIdAndTimeStartAtBetweenOrderByTimeStartAtDesc(String userId, Instant from, Instant to);

    List<WorkoutSession> findTop50ByUserIdOrderByTimeStartAtDesc(String userId);
}