package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.enums.WorkoutTrackingStatus;
import com.example.dacn2_beserver.model.health.WorkoutTracking;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface WorkoutTrackingRepository extends MongoRepository<WorkoutTracking, String> {
    Optional<WorkoutTracking> findByIdAndUserId(String id, String userId);

    Optional<WorkoutTracking> findTop1ByUserIdAndStatusOrderByStartedAtDesc(String userId, WorkoutTrackingStatus status);
}
