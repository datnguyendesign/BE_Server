package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.common.ApiResponse;
import com.example.dacn2_beserver.dto.nutrition.CancelFoodLogRequest;
import com.example.dacn2_beserver.dto.nutrition.ConfirmFoodLogRequest;
import com.example.dacn2_beserver.dto.nutrition.FoodLogResponse;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.nutrition.FoodLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/nutrition/logs")
@RequiredArgsConstructor
public class NutritionLogController {

    private static final Logger log = LoggerFactory.getLogger(NutritionLogController.class);
    private final FoodLogService foodLogService;

    @PostMapping("/confirm")
    public ApiResponse<FoodLogResponse> confirm(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody ConfirmFoodLogRequest req
    ) {
        return ApiResponse.ok(foodLogService.confirm(principal.userId(), req));
    }

    @GetMapping
    public ApiResponse<List<FoodLogResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ApiResponse.ok(foodLogService.list(principal.userId(), from, to));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id
    ) {
        foodLogService.delete(principal.userId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) CancelFoodLogRequest req
    ) {
        // No-op by design (analytics-ready). Do not persist.
        String reason = req != null ? req.getReason() : null;
        log.info("Nutrition cancel: userId={}, reason={}, candidates={}",
                principal.userId(),
                reason,
                req != null ? req.getCandidateCodes() : null);

        return ApiResponse.ok(null);
    }
}