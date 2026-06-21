    package com.example.dacn2_beserver.service.auth;

    import com.example.dacn2_beserver.dto.auth.AuthTokensResponse;
    import com.example.dacn2_beserver.dto.user.UserResponse;
    import com.example.dacn2_beserver.exception.UserNotFoundException;
    import com.example.dacn2_beserver.model.user.User;
    import com.example.dacn2_beserver.repository.UserRepository;
    import com.example.dacn2_beserver.security.AuthPrincipal;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Service;

    @Service
    @RequiredArgsConstructor
    public class AuthService {
        private final SessionService sessionService;
        private final JwtService jwtService;
        private final UserRepository userRepository;

        public AuthTokensResponse refresh(String refreshToken) {
            var issued = sessionService.rotate(refreshToken);
            String access = jwtService.generateAccessToken(issued.session().getUserId(), issued.session().getId());

            return AuthTokensResponse.builder()
                    .accessToken(access)
                    .refreshToken(issued.refreshTokenPlain())
                    .expiresInSeconds(jwtService.getExpirationSeconds())
                    .build();
        }

        public void logout(AuthPrincipal principal) {
            sessionService.revoke(principal.userId(), principal.sessionId(), "LOGOUT");
        }

        public UserResponse me(AuthPrincipal principal) {
            User u = userRepository.findById(principal.userId()).orElseThrow(() -> new UserNotFoundException(principal.getName()));
            return UserResponse.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .primaryEmail(u.getPrimaryEmail())
                    .status(u.getStatus())
                    .roles(u.getRoles())
                    .lastLoginAt(u.getLastLoginAt())
                    .createdAt(u.getCreatedAt())
                    .updatedAt(u.getUpdatedAt())
                    .build();
        }
    }