package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.auth.UserCredential;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserCredentialRepository extends MongoRepository<UserCredential, String> {
    Optional<UserCredential> findByUserId(String userId);

    boolean existsByUserId(String userId);
}