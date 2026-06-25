package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.ai.AiFeedbackRequest;
import com.example.dacn2_beserver.dto.ai.AiFoodPredictRequest;
import com.example.dacn2_beserver.dto.ai.AiFoodPredictResponse;
import com.example.dacn2_beserver.dto.ai.DailyAnalysisRequest;
import com.example.dacn2_beserver.dto.ai.DailyAnalysisResponse;
import com.example.dacn2_beserver.dto.common.ApiResponse;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.ai.AiAnalysisService;
import com.example.dacn2_beserver.service.ai.AiFoodClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiFoodClient aiFoodClient;
    private final AiAnalysisService aiAnalysisService;

    @PostMapping(value = "/food-predict", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AiFoodPredictResponse test(@RequestBody AiFoodPredictRequest req) {
        return aiFoodClient.predictFoodByUrl(req.getImageUrl());
    }

    @PostMapping(value = "/daily-analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DailyAnalysisResponse> dailyAnalysis(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody DailyAnalysisRequest req) {
        return ApiResponse.ok(aiAnalysisService.analyzeDaily(principal.userId(), req));
    }

    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> feedback(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody AiFeedbackRequest req
    ) {
        aiAnalysisService.saveFeedback(principal.userId(), req);
        return ApiResponse.ok(null);
    }
}
