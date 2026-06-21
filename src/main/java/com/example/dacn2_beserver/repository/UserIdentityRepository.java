package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.auth.UserIdentity;
import com.example.dacn2_beserver.model.enums.IdentityProvider;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserIdentityRepository extends MongoRepository<UserIdentity, String> {
    Optional<UserIdentity> findByProviderAndNormalized(IdentityProvider provider, String normalized);

    List<UserIdentity> findAllByUserId(String userId);

    boolean existsByProviderAndNormalized(IdentityProvider provider, String normalized);

    boolean existsByProviderAndEmailAtProvider(IdentityProvider provider, String emailAtProvider);

    void deleteAllByUserId(String userId);
}