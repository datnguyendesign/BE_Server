package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.health.DailyAggregate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyAggregateRepository extends MongoRepository<DailyAggregate, String> {
    Optional<DailyAggregate> findByUserIdAndDate(String userId, LocalDate date);

    List<DailyAggregate> findAllByUserIdAndDateBetween(String userId, LocalDate from, LocalDate to);
}