package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.config.RateLimitProperties;
import com.example.dacn2_beserver.dto.common.ApiResponse;
import com.example.dacn2_beserver.dto.media.PresignGetResponse;
import com.example.dacn2_beserver.dto.media.PresignPutRequest;
import com.example.dacn2_beserver.dto.media.PresignPutResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.ratelimit.RedisRateLimitService;
import com.example.dacn2_beserver.service.storage.ChatMediaS3Service;
import com.example.dacn2_beserver.service.storage.NutritionS3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final NutritionS3Service nutritionS3Service;
    private final ChatMediaS3Service chatMediaS3Service;
    private final RedisRateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProps;

    @Value("${aws.s3.chat.presign.get-ttl-seconds:600}")
    private long chatGetTtlSeconds;

    @PostMapping(value = "/nutrition/presign-put", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PresignPutResponse> presignNutritionPut(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody PresignPutRequest req
    ) {
        rateLimitService.checkOrThrow(
                "rl:media:presign:" + principal.userId(),
                rateLimitProps.getMedia().getPresign().getLimit(),
                rateLimitProps.getMedia().getPresign().getWindowSeconds()
        );

        long sizeBytes = requirePositiveSizeBytes(req.getSizeBytes());
        String contentType = requireContentType(req.getContentType());

        var r = nutritionS3Service.presignPut(
                principal.userId(),
                contentType,
                sizeBytes
        );

        return ApiResponse.ok(PresignPutResponse.builder()
                .objectKey(r.objectKey())
                .uploadUrl(r.uploadUrl())
                .expiresAt(r.expiresAt())
                .publicUrl(null)
                .build());
    }

    @PostMapping(value = "/chat/presign-put", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PresignPutResponse> presignChatPut(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody PresignPutRequest req
    ) {
        rateLimitService.checkOrThrow(
                "rl:media:presign:" + principal.userId(),
                rateLimitProps.getMedia().getPresign().getLimit(),
                rateLimitProps.getMedia().getPresign().getWindowSeconds()
        );

        long sizeBytes = requirePositiveSizeBytes(req.getSizeBytes());
        String contentType = requireContentType(req.getContentType());

        var r = chatMediaS3Service.presignPut(
                principal.userId(),
                contentType,
                sizeBytes
        );

        // Chat bucket should be private. Do NOT return public URL here.
        return ApiResponse.ok(PresignPutResponse.builder()
                .objectKey(r.objectKey())
                .uploadUrl(r.uploadUrl())
                .expiresAt(r.expiresAt())
                .publicUrl(null)
                .build());
    }

    @GetMapping("/chat/presign-get")
    public ApiResponse<PresignGetResponse> presignChatGet(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String objectKey
    ) {
        chatMediaS3Service.assertOwnedByUser(principal.userId(), objectKey);

        String url = chatMediaS3Service.presignGetUrl(objectKey);
        Instant expiresAt = Instant.now().plusSeconds(chatGetTtlSeconds);

        return ApiResponse.ok(PresignGetResponse.builder()
                .url(url)
                .expiresAt(expiresAt)
                .build());
    }

    private long requirePositiveSizeBytes(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "sizeBytes is required and must be > 0");
        }
        return sizeBytes;
    }

    private String requireContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "contentType is required");
        }
        return contentType.trim();
    }
}