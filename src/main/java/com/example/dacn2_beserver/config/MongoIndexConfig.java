package com.example.dacn2_beserver.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoIndexConfig {
    @Bean
    ApplicationRunner ensureTtlIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            ensureTtl(mongoTemplate, "otp_requests", "expiresAt", "otp_expires_ttl");
            ensureTtl(mongoTemplate, "link_tickets", "expiresAt", "link_ticket_expires_ttl");
            ensureTtl(mongoTemplate, "sessions", "expiresAt", "session_expires_ttl");
        };
    }

    private void ensureTtl(MongoTemplate mongoTemplate, String collection, String field, String indexName) {
        var ops = mongoTemplate.indexOps(collection);
        var infos = ops.getIndexInfo();

        // Nếu đã có index cùng tên nhưng TTL không đúng -> drop
        infos.stream()
                .filter(i -> indexName.equals(i.getName()))
                .findFirst()
                .ifPresent(i -> {
                    Long expireAfter = i.getExpireAfter().map(Duration::getSeconds).orElse(null); // seconds (nullable)
                    if (expireAfter == null || expireAfter != 0L) {
                        ops.dropIndex(indexName);
                    }
                });

        // Nếu tồn tại index khác trên field nhưng không TTL -> drop để tránh trùng và tránh “sai TTL”
        infos.stream()
                .filter(i -> !indexName.equals(i.getName()))
                .filter(i -> i.getIndexFields().stream().anyMatch(f -> field.equals(f.getKey())))
                .forEach(i -> {
                    Long expireAfter = i.getExpireAfter().map(Duration::getSeconds).orElse(null);
                    if (expireAfter == null || expireAfter != 0L) {
                        ops.dropIndex(i.getName());
                    }
                });

        // Ensure TTL chuẩn: expireAfterSeconds = 0
        ops.createIndex(new Index()
                .on(field, Sort.Direction.ASC)
                .named(indexName)
                .expire(0, TimeUnit.SECONDS));
    }
}