package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.ai.RecommendationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecommendationLogRepository extends MongoRepository<RecommendationLog, String> {
    List<RecommendationLog> findTop50ByUserIdOrderByCreatedAtDesc(String userId);
}