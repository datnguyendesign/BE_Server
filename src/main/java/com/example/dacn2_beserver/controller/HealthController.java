package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.common.ApiResponse;
import com.example.dacn2_beserver.dto.health.*;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.health.CalendarService;
import com.example.dacn2_beserver.service.health.SleepService;
import com.example.dacn2_beserver.service.health.SummaryService;
import com.example.dacn2_beserver.service.health.WaterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final WaterService waterService;
    private final SummaryService summaryService;
    private final SleepService sleepService;
    private final CalendarService calendarService;

    // -------- WATER --------

    @PostMapping("/water")
    public ApiResponse<WaterLogResponse> createWater(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateWaterLogRequest req
    ) {
        return ApiResponse.ok(waterService.create(principal.userId(), req));
    }

    @GetMapping("/water")
    public ApiResponse<List<WaterLogResponse>> listWater(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ApiResponse.ok(waterService.listResponses(principal.userId(), from, to));
    }

    // -------- SLEEP --------

    @PostMapping("/sleep")
    public ApiResponse<SleepSessionResponse> createSleep(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateSleepSessionRequest req
    ) {
        return ApiResponse.ok(sleepService.create(principal.userId(), req));
    }

    @GetMapping("/sleep")
    public ApiResponse<List<SleepSessionResponse>> listSleep(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ApiResponse.ok(sleepService.listResponses(principal.userId(), from, to));
    }

    // -------- SUMMARY --------

    @GetMapping("/summary/{date}")
    public ApiResponse<DailyAggregateResponse> summaryByDate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.ok(summaryService.getByDate(principal.userId(), date));
    }

    @GetMapping("/summary")
    public ApiResponse<List<DailyAggregateResponse>> summaryRange(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.ok(summaryService.getRange(principal.userId(), from, to));
    }

    // -------- CALENDAR (single-day for calendar click) --------

    @GetMapping("/calendar")
    public ApiResponse<CalendarDaySummaryResponse> calendarDay(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.ok(calendarService.getDay(principal.userId(), date));
    }
}