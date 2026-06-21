package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.common.ApiResponse;
import com.example.dacn2_beserver.dto.nutrition.NutritionAnalyzeRequest;
import com.example.dacn2_beserver.dto.nutrition.NutritionAnalyzeResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.nutrition.NutritionAnalyzeService;
import com.example.dacn2_beserver.service.storage.NutritionS3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionS3Service nutritionS3Service;
    private final NutritionAnalyzeService nutritionAnalyzeService;

    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<NutritionAnalyzeResponse> analyze(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody NutritionAnalyzeRequest req
    ) {
        String objectKey = (req != null) ? req.getObjectKey() : null;
        if (objectKey == null || objectKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "objectKey is required");
        }

        nutritionS3Service.assertOwnedByUser(principal.userId(), objectKey);

        String imageUrl = nutritionS3Service.presignGetUrl(objectKey);

        try {
            return ApiResponse.ok(nutritionAnalyzeService.analyzeByImageUrl(imageUrl));
        } finally {
            // Delete immediately after AI returns (best effort)
            nutritionS3Service.deleteObjectBestEffort(objectKey);
        }
    }
}