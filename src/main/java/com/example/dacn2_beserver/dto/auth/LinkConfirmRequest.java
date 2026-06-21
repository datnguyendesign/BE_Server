package com.example.dacn2_beserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkConfirmRequest {
    @NotBlank
    private String linkTicketId; // lt_xxx
    private String deviceId;
}