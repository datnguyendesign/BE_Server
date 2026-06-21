package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.user.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);

    Optional<User> findByPrimaryEmail(String primaryEmail);

    boolean existsByUsername(String username);

    boolean existsByPrimaryEmail(String primaryEmail);
}