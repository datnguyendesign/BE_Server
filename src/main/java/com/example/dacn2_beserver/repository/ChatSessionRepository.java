package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.ai.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
    Optional<ChatSession> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    List<ChatSession> findTop50ByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String userId);
}