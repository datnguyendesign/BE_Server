package com.example.dacn2_beserver.service.auth;

import com.example.dacn2_beserver.exception.GoogleIDTokenInvalidException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoogleIdTokenVerifierService {

    private final JwtDecoder decoder;
    private final String clientId;

    public GoogleIdTokenVerifierService(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId
    ) {
        this.clientId = clientId;

        // Google public keys (JWK)
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();

        // Validate issuer + audience
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("https://accounts.google.com");
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            List<String> aud = token.getAudience();
            if (aud != null && aud.contains(clientId)) return OAuth2TokenValidatorResult.success();
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        };

        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        this.decoder = nimbus;
    }

    public GoogleClaims verify(String idToken) {
        try {
            Jwt jwt = decoder.decode(idToken);

            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
            String name = jwt.getClaimAsString("name");
            String picture = jwt.getClaimAsString("picture");

            if (sub == null || sub.isBlank()) throw new GoogleIDTokenInvalidException("Missing sub claim");

            return new GoogleClaims(sub, email, emailVerified, name, picture);
        } catch (JwtException e) {
            throw new GoogleIDTokenInvalidException(e.getMessage());
        }
    }

    @Getter
    public static class GoogleClaims {
        private final String sub;
        private final String email;
        private final Boolean emailVerified;
        private final String name;
        private final String picture;

        public GoogleClaims(String sub, String email, Boolean emailVerified, String name, String picture) {
            this.sub = sub;
            this.email = email;
            this.emailVerified = emailVerified;
            this.name = name;
            this.picture = picture;
        }
    }
}