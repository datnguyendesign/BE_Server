package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.auth.LinkTicket;
import com.example.dacn2_beserver.model.enums.LinkTicketStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface LinkTicketRepository extends MongoRepository<LinkTicket, String> {
    Optional<LinkTicket> findByIdAndStatusAndExpiresAtAfter(String id, LinkTicketStatus status, Instant now);
}