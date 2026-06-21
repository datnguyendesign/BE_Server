package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.ai.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findTop100BySessionIdOrderByCreatedAtAsc(String sessionId);

    // Pagination: fetch messages older than a cursor
    List<ChatMessage> findBySessionIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            String sessionId,
            Instant before,
            Pageable pageable
    );
}