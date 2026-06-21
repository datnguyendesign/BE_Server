package com.example.dacn2_beserver.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.dacn2_beserver.model.health.WorkoutPoint;

public interface WorkoutPointRepository extends MongoRepository<WorkoutPoint, String> {
    List<WorkoutPoint> findAllByTrackingIdAndTsBetweenOrderByTsAsc(String trackingId, Instant from, Instant to);
}
