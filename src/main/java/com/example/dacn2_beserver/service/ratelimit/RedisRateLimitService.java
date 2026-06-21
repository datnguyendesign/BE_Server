package com.example.dacn2_beserver.service.ratelimit;

import com.example.dacn2_beserver.config.RateLimitProperties;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> rateLimitLuaScript;
    private final RateLimitProperties props;

    /**
     * Fail-open on Redis errors to avoid taking down the API.
     * Can be disabled via ratelimit.enabled=false.
     */
    public void checkOrThrow(String key, long limit, long windowSeconds) {
        if (!props.isEnabled()) {
            return;
        }

        try {
            Long allowed = redis.execute(
                    rateLimitLuaScript,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds)
            );

            if (allowed == null || allowed == 0L) {
                throw new ApiException(ErrorCode.RATE_LIMITED, "Too many requests");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limit bypassed due to Redis error: {}", e.getMessage());
        }
    }
}