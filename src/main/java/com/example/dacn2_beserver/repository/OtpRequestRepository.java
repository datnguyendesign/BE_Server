package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.auth.OtpRequest;
import com.example.dacn2_beserver.model.enums.OtpPurpose;
import com.example.dacn2_beserver.model.enums.OtpStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpRequestRepository extends MongoRepository<OtpRequest, String> {
    Optional<OtpRequest> findTopByIdentifierNormalizedAndPurposeOrderByCreatedAtDesc(
            String identifierNormalized,
            OtpPurpose purpose
    );

    List<OtpRequest> findAllByIdentifierNormalizedAndPurposeAndStatusAndExpiresAtAfter(
            String identifierNormalized,
            OtpPurpose purpose,
            OtpStatus status,
            Instant now
    );
}