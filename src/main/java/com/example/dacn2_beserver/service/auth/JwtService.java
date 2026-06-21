package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final Key key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateAccessToken(String userId, String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);

        return Jwts.builder()
                .setSubject(userId)
                .addClaims(Map.of("sid", sessionId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000L;
    }

    public ParsedToken parseAndValidate(String jwt) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            String userId = claims.getSubject();
            String sid = claims.get("sid", String.class);
            if (userId == null || sid == null) throw new InvalidTokenException();
            return new ParsedToken(userId, sid);
        } catch (Exception e) {
            throw new InvalidTokenException();
        }
    }

    public record ParsedToken(String userId, String sessionId) {
    }
}