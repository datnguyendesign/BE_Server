package com.example.dacn2_beserver.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dacn2_beserver.dto.health.PushWorkoutPointsRequest;
import com.example.dacn2_beserver.dto.health.StartWorkoutTrackingRequest;
import com.example.dacn2_beserver.dto.health.StartWorkoutTrackingResponse;
import com.example.dacn2_beserver.dto.health.UpsertWorkoutStepsRequest;
import com.example.dacn2_beserver.dto.health.WorkoutSessionResponse;
import com.example.dacn2_beserver.dto.health.WorkoutTrackingLiveResponse;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.health.WorkoutTrackingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/health/workouts")
@RequiredArgsConstructor
public class WorkoutTrackingController {

    private final WorkoutTrackingService workoutTrackingService;

    // ===== tracking lifecycle =====

    @PostMapping("/tracking/start")
    public StartWorkoutTrackingResponse start(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody StartWorkoutTrackingRequest req
    ) {
        return workoutTrackingService.start(principal.userId(), req);
    }

    @PostMapping("/tracking/{trackingId}/pause")
    public void pause(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String trackingId
    ) {
        workoutTrackingService.pause(principal.userId(), trackingId);
    }

    @PostMapping("/tracking/{trackingId}/resume")
    public void resume(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String trackingId
    ) {
        workoutTrackingService.resume(principal.userId(), trackingId);
    }

    @PostMapping("/tracking/{trackingId}/points")
    public WorkoutTrackingLiveResponse pushPoints(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String trackingId,
            @Valid @RequestBody PushWorkoutPointsRequest req
    ) {
        return workoutTrackingService.pushPoints(principal.userId(), trackingId, req);
    }

    @PostMapping("/tracking/{trackingId}/steps")
    public WorkoutTrackingLiveResponse upsertSteps(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String trackingId,
            @Valid @RequestBody UpsertWorkoutStepsRequest req
    ) {
        return workoutTrackingService.upsertSteps(principal.userId(), trackingId, req);
    }

    @PostMapping("/tracking/{trackingId}/end")
    public WorkoutSessionResponse end(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String trackingId
    ) {
        return workoutTrackingService.end(principal.userId(), trackingId);
    }

    // ===== workouts (ended sessions) =====

    @GetMapping
    public List<WorkoutSessionResponse> listWorkouts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return workoutTrackingService.listWorkouts(principal.userId(), from, to);
    }
}
