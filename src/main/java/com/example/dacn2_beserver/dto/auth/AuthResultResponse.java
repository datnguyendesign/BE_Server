package com.example.dacn2_beserver.dto.auth;

import com.example.dacn2_beserver.dto.user.UserResponse;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResultResponse {
    private UserResponse user;
    private AuthTokensResponse tokens;

    // Nếu cần link confirm:
    private Boolean linkRequired;
    private String linkTicketId;
    private String message;

    private Boolean isNewUser;
}