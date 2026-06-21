package com.example.dacn2_beserver.service.health;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.example.dacn2_beserver.dto.health.PushWorkoutPointsRequest;
import com.example.dacn2_beserver.dto.health.SourceMetaDto;
import com.example.dacn2_beserver.dto.health.StartWorkoutTrackingRequest;
import com.example.dacn2_beserver.dto.health.StartWorkoutTrackingResponse;
import com.example.dacn2_beserver.dto.health.TimeRangeDto;
import com.example.dacn2_beserver.dto.health.UpsertWorkoutStepsRequest;
import com.example.dacn2_beserver.dto.health.WorkoutSessionResponse;
import com.example.dacn2_beserver.dto.health.WorkoutTrackingLiveResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import com.example.dacn2_beserver.model.common.GeoPoint;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.WorkoutTrackingStatus;
import com.example.dacn2_beserver.model.enums.WorkoutType;
import com.example.dacn2_beserver.model.health.WorkoutPoint;
import com.example.dacn2_beserver.model.health.WorkoutSession;
import com.example.dacn2_beserver.model.health.WorkoutTracking;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.UserRepository;
import com.example.dacn2_beserver.repository.WorkoutPointRepository;
import com.example.dacn2_beserver.repository.WorkoutSessionRepository;
import com.example.dacn2_beserver.repository.WorkoutTrackingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkoutTrackingService {

    private static final double MAX_ACCURACY_M = 200.0;

    private final WorkoutTrackingRepository workoutTrackingRepository;
    private final WorkoutPointRepository workoutPointRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final UserRepository userRepository;
    private final DailyAggregateService dailyAggregateService;

    /**
     * Sửa đổi: Tự động kết thúc các phiên đang chạy (ACTIVE/PAUSED) trước khi bắt đầu phiên mới.
     */
    public StartWorkoutTrackingResponse start(String userId, StartWorkoutTrackingRequest req) {
        if (req.getWorkoutType() == null) throw new ApiException(ErrorCode.VALIDATION_ERROR, "workoutType is required");

        // 1. Tìm và đóng phiên ACTIVE nếu có
        workoutTrackingRepository.findTop1ByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutTrackingStatus.ACTIVE)
                .ifPresent(this::autoEndSession);

        // 2. Tìm và đóng phiên PAUSED nếu có
        workoutTrackingRepository.findTop1ByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutTrackingStatus.PAUSED)
                .ifPresent(this::autoEndSession);

        // 3. Bắt đầu phiên mới
        Instant now = Instant.now();
        WorkoutTracking t = WorkoutTracking.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .workoutType(req.getWorkoutType())
                .status(WorkoutTrackingStatus.ACTIVE)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        workoutTrackingRepository.save(t);

        return StartWorkoutTrackingResponse.builder()
                .trackingId(t.getId())
                .startedAt(t.getStartedAt())
                .build();
    }

    /**
     * Helper để tự động đóng phiên cũ một cách an toàn
     */
    private void autoEndSession(WorkoutTracking t) {
        Instant now = Instant.now();
        t.setStatus(WorkoutTrackingStatus.ENDED);
        t.setEndedAt(now);
        t.setUpdatedAt(now);
        workoutTrackingRepository.save(t);
        // Log để debug nếu cần
        System.out.println("Auto-ended stuck session: " + t.getId());
    }

    public void pause(String userId, String trackingId) {
        WorkoutTracking t = requireTracking(userId, trackingId);
        if (t.getStatus() == WorkoutTrackingStatus.ENDED) throw new ApiException(ErrorCode.BAD_REQUEST, "Tracking already ended");
        if (t.getStatus() == WorkoutTrackingStatus.PAUSED) return;

        t.setStatus(WorkoutTrackingStatus.PAUSED);
        t.setPausedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        workoutTrackingRepository.save(t);
    }

    public void resume(String userId, String trackingId) {
        WorkoutTracking t = requireTracking(userId, trackingId);
        if (t.getStatus() == WorkoutTrackingStatus.ENDED) throw new ApiException(ErrorCode.BAD_REQUEST, "Tracking already ended");
        if (t.getStatus() != WorkoutTrackingStatus.PAUSED) return;

        Instant now = Instant.now();
        if (t.getPausedAt() != null) {
            t.setTotalPausedMs(t.getTotalPausedMs() + Duration.between(t.getPausedAt(), now).toMillis());
        }
        t.setPausedAt(null);
        t.setStatus(WorkoutTrackingStatus.ACTIVE);
        t.setUpdatedAt(now);
        workoutTrackingRepository.save(t);
    }

    public WorkoutTrackingLiveResponse upsertSteps(String userId, String trackingId, UpsertWorkoutStepsRequest req) {
        WorkoutTracking t = requireTracking(userId, trackingId);
        if (t.getStatus() == WorkoutTrackingStatus.ENDED) throw new ApiException(ErrorCode.BAD_REQUEST, "Tracking already ended");

        int incoming = req.getStepsTotal() == null ? 0 : Math.max(0, req.getStepsTotal());
        int current = t.getSteps() == null ? 0 : t.getSteps();
        t.setSteps(Math.max(current, incoming));
        t.setUpdatedAt(Instant.now());
        workoutTrackingRepository.save(t);

        return live(t, userId);
    }

    public WorkoutTrackingLiveResponse pushPoints(String userId, String trackingId, PushWorkoutPointsRequest req) {
        WorkoutTracking t = requireTracking(userId, trackingId);
        if (t.getStatus() != WorkoutTrackingStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Tracking is not ACTIVE");
        }

        if (req.getPoints() == null || req.getPoints().isEmpty()) return live(t, userId);

        List<PushWorkoutPointsRequest.PointDto> points = new ArrayList<>(req.getPoints());
        points.sort(Comparator.comparingLong(PushWorkoutPointsRequest.PointDto::getTsMs));

        Instant lastTs = (t.getLastPoint() == null) ? null : t.getLastPoint().getTs();

        List<WorkoutPoint> toInsert = new ArrayList<>();
        for (var p : points) {
            Instant ts = Instant.ofEpochMilli(p.getTsMs());
            if (lastTs != null && !ts.isAfter(lastTs)) continue;

            double lat = p.getLat();
            double lng = p.getLng();
            Double acc = p.getAccuracyM();

            if (acc != null && acc > MAX_ACCURACY_M) {
                lastTs = ts;
                continue;
            }

            if (t.getStartLocation() == null) {
                t.setStartLocation(GeoPoint.builder().lat(lat).lng(lng).build());
            }

            if (t.getLastPoint() != null) {
                double segM = haversineMeters(
                        t.getLastPoint().getLat(), t.getLastPoint().getLng(),
                        lat, lng
                );
                long dtMs = Duration.between(t.getLastPoint().getTs(), ts).toMillis();
                if (dtMs > 0) {
                    double speed = segM / (dtMs / 1000.0);
                    if (isSpeedPlausible(t.getWorkoutType(), speed)) {
                        t.setDistanceKm(t.getDistanceKm() + (segM / 1000.0));
                    }
                }
            }

            t.setLastPoint(WorkoutTracking.LastPoint.builder()
                    .ts(ts)
                    .lat(lat)
                    .lng(lng)
                    .accuracyM(acc)
                    .build());

            sampleRoute(t, lat, lng);

            toInsert.add(WorkoutPoint.builder()
                    .trackingId(trackingId)
                    .userId(userId)
                    .ts(ts)
                    .lat(lat)
                    .lng(lng)
                    .accuracyM(p.getAccuracyM())
                    .speedMps(p.getSpeedMps())
                    .build());

            lastTs = ts;
        }

        if (!toInsert.isEmpty()) {
            try {
                workoutPointRepository.saveAll(toInsert);
            } catch (DuplicateKeyException ignored) {}
        }

        if (t.getLastPoint() != null) {
            t.setEndLocation(GeoPoint.builder().lat(t.getLastPoint().getLat()).lng(t.getLastPoint().getLng()).build());
        }

        t.setUpdatedAt(Instant.now());
        workoutTrackingRepository.save(t);

        return live(t, userId);
    }

    public WorkoutSessionResponse end(String userId, String trackingId) {
        WorkoutTracking t = requireTracking(userId, trackingId);
        if (t.getStatus() == WorkoutTrackingStatus.ENDED) throw new ApiException(ErrorCode.BAD_REQUEST, "Tracking already ended");

        Instant now = Instant.now();

        if (t.getStatus() == WorkoutTrackingStatus.PAUSED && t.getPausedAt() != null) {
            t.setTotalPausedMs(t.getTotalPausedMs() + Duration.between(t.getPausedAt(), now).toMillis());
            t.setPausedAt(null);
        }

        t.setStatus(WorkoutTrackingStatus.ENDED);
        t.setEndedAt(now);
        t.setUpdatedAt(now);
        workoutTrackingRepository.save(t);

        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));
        double weightKg = (u.getProfile() != null && u.getProfile().getWeightKg() != null)
                ? u.getProfile().getWeightKg()
                : 60.0;

        int calories = estimateCaloriesOut(t.getWorkoutType(), weightKg, t.getDistanceKm());
        int steps = t.getSteps() == null ? 0 : t.getSteps();

        WorkoutSession ws = WorkoutSession.builder()
                .userId(userId)
                .workoutType(t.getWorkoutType())
                .time(TimeRange.builder().startAt(t.getStartedAt()).endAt(now).build())
                .distanceKm(t.getDistanceKm())
                .steps(steps)
                .caloriesOut(calories)
                .routeSample(t.getRouteSample())
                .startLocation(t.getStartLocation())
                .endLocation(t.getEndLocation())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ws = workoutSessionRepository.save(ws);
        dailyAggregateService.addWorkout(userId, now, steps, t.getDistanceKm(), calories);

        return toWorkoutSessionResponse(ws);
    }

    public List<WorkoutSessionResponse> listWorkouts(String userId, Instant from, Instant to) {
        return workoutSessionRepository.findAllByUserIdAndTimeStartAtBetweenOrderByTimeStartAtDesc(userId, from, to)
                .stream()
                .map(this::toWorkoutSessionResponse)
                .toList();
    }

    private WorkoutTracking requireTracking(String userId, String trackingId) {
        return workoutTrackingRepository.findByIdAndUserId(trackingId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Tracking not found"));
    }

    private long activeDurationMs(WorkoutTracking t, Instant now) {
        if (t.getStartedAt() == null) return 0L;
        long total = Duration.between(t.getStartedAt(), now).toMillis();
        long paused = t.getTotalPausedMs();
        if (t.getStatus() == WorkoutTrackingStatus.PAUSED && t.getPausedAt() != null) {
            paused += Duration.between(t.getPausedAt(), now).toMillis();
        }
        return Math.max(0L, total - paused);
    }

    private WorkoutTrackingLiveResponse live(WorkoutTracking t, String userId) {
        Instant now = Instant.now();
        long activeMs = activeDurationMs(t, now);

        int pace = 0;
        double distanceKm = t.getDistanceKm();
        if (distanceKm > 0.05) {
            double sec = activeMs / 1000.0;
            pace = (int) Math.round(sec / distanceKm);
        }

        User u = userRepository.findById(userId).orElse(null);
        double weightKg = (u != null && u.getProfile() != null && u.getProfile().getWeightKg() != null)
                ? u.getProfile().getWeightKg()
                : 60.0;
        int calories = estimateCaloriesOut(t.getWorkoutType(), weightKg, t.getDistanceKm());

        return WorkoutTrackingLiveResponse.builder()
                .trackingId(t.getId())
                .distanceKm(t.getDistanceKm())
                .activeDurationMs(activeMs)
                .steps(t.getSteps() == null ? 0 : t.getSteps())
                .caloriesOut(calories)
                .avgPaceSecPerKm(pace == 0 ? null : pace)
                .updatedAt(t.getUpdatedAt() == null ? now : t.getUpdatedAt())
                .build();
    }

    private boolean isSpeedPlausible(WorkoutType type, double speedMps) {
        double vmax = 10.0;
        if (type == WorkoutType.WALK) vmax = 3.5;
        if (type == WorkoutType.RUN) vmax = 8.0;
        return speedMps >= 0 && speedMps <= vmax;
    }

    private void sampleRoute(WorkoutTracking t, double lat, double lng) {
        long n = t.getPointCount() + 1;
        t.setPointCount(n);
        if (t.getRouteSample() == null) t.setRouteSample(new ArrayList<>());
        if (t.getRouteSample().size() < 100 || (n % 10 == 0)) {
            t.getRouteSample().add(GeoPoint.builder().lat(lat).lng(lng).build());
            if (t.getRouteSample().size() > 150) {
                t.setRouteSample(t.getRouteSample().subList(t.getRouteSample().size() - 150, t.getRouteSample().size()));
            }
        }
    }

    private int estimateCaloriesOut(WorkoutType type, double weightKg, double distanceKm) {
        double k = 0.9;
        if (type == WorkoutType.WALK) k = 0.8;
        if (type == WorkoutType.RUN) k = 1.0;
        double cal = weightKg * Math.max(0.0, distanceKm) * k;
        return (int) Math.round(cal);
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private WorkoutSessionResponse toWorkoutSessionResponse(WorkoutSession s) {
        var time = s.getTime() == null ? null : TimeRangeDto.builder()
                .startAt(s.getTime().getStartAt())
                .endAt(s.getTime().getEndAt())
                .build();

        List<WorkoutSessionResponse.GeoPointDto> sample = null;
        if (s.getRouteSample() != null) {
            sample = s.getRouteSample().stream()
                    .map(p -> WorkoutSessionResponse.GeoPointDto.builder().lat(p.getLat()).lng(p.getLng()).build())
                    .toList();
        }

        var start = s.getStartLocation() == null ? null
                : WorkoutSessionResponse.GeoPointDto.builder().lat(s.getStartLocation().getLat()).lng(s.getStartLocation().getLng()).build();
        var end = s.getEndLocation() == null ? null
                : WorkoutSessionResponse.GeoPointDto.builder().lat(s.getEndLocation().getLat()).lng(s.getEndLocation().getLng()).build();

        return WorkoutSessionResponse.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .workoutType(s.getWorkoutType())
                .time(time)
                .distanceKm(s.getDistanceKm())
                .steps(s.getSteps())
                .caloriesOut(s.getCaloriesOut())
                .routeSample(sample)
                .startLocation(start)
                .endLocation(end)
                .status(s.getStatus())
                .meta(s.getMeta() == null ? null : SourceMetaDto.builder()
                        .source(s.getMeta().getSource())
                        .deviceId(s.getMeta().getDeviceId())
                        .idempotencyKey(s.getMeta().getIdempotencyKey())
                        .rawRef(s.getMeta().getRawRef())
                        .build())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}