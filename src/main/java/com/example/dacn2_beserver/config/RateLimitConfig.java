package com.example.dacn2_beserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RateLimitConfig {

    @Bean
    public DefaultRedisScript<Long> rateLimitLuaScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);

        // Returns 1 if allowed, 0 if blocked.
        script.setScriptText("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                      redis.call('EXPIRE', KEYS[1], ARGV[2])
                    end
                    if current > tonumber(ARGV[1]) then
                      return 0
                    end
                    return 1
                """);

        return script;
    }
}