package com.example.dacn2_beserver.dto.auth;

import com.example.dacn2_beserver.model.enums.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequestCreateRequest {

    @NotNull
    private OtpChannel channel; // EMAIL hoặc SMS

    @NotBlank
    private String identifier;  // email hoặc phone
}