package com.example.dacn2_beserver.dto.auth;

import com.example.dacn2_beserver.model.enums.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyRequest {
    // Ưu tiên dùng otpRequestId vì chắc chắn đúng OTP.
    // Nếu FE không giữ otpRequestId, thì bắt buộc gửi channel.
    private String otpRequestId;

    private OtpChannel channel;

    @NotBlank
    private String identifier;

    @NotBlank
    private String code;

    private String deviceId;
}