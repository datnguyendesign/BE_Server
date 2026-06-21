package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * DailyAggregateService
 * <p>
 * Mục tiêu:
 * - Upsert DailyAggregate theo ngày (theo timezone của user)
 * - Cập nhật các metric: water, sleep
 * - Sinh highlights + summary (rule-based MVP)
 * <p>
 * Quy ước:
 * - Water thuộc về ngày của loggedAt (theo timezone user)
 * - Sleep thuộc về ngày "thức dậy" (endAt) theo timezone user
 * - MVP: cộng dồn (support multiple water logs, multiple sleep sessions/nap trong 1 ngày)
 */
@Service
@RequiredArgsConstructor
public class DailyAggregateService {

    // ===== Defaults (MVP) =====
    private static final String DEFAULT_TZ = "UTC";
    private static final int DEFAULT_WATER_GOAL_ML = 2000;
    private static final int DEFAULT_SLEEP_GOAL_MIN = 420; // 7h
    private static final int DEFAULT_CALORIES_IN_GOAL = 2000;
    private static final int DEFAULT_CALORIES_OUT_GOAL = 500;
    private final DailyAggregateRepository dailyAggregateRepository;
    private final UserRepository userRepository;


    public DailyAggregate addWater(String userId, Instant loggedAt, int deltaMl) {
        User user = requireUser(userId);
        ZoneId zoneId = userZone(user);

        LocalDate date = LocalDateTime.ofInstant(loggedAt, zoneId).toLocalDate();
        DailyAggregate agg = findOrCreate(userId, date);

        int current = nvl(agg.getWaterMl());
        agg.setWaterMl(Math.max(0, current + deltaMl));
        touch(agg);

        applyWaterHighlights(user, agg);

        return dailyAggregateRepository.save(agg);
    }

    public DailyAggregate addCaloriesIn(String userId, Instant loggedAt, int deltaCaloriesIn) {
        User user = requireUser(userId);
        ZoneId zoneId = userZone(user);

        LocalDate date = LocalDateTime.ofInstant(loggedAt, zoneId).toLocalDate();
        DailyAggregate agg = findOrCreate(userId, date);

        int current = nvl(agg.getCaloriesIn());
        agg.setCaloriesIn(Math.max(0, current + deltaCaloriesIn));


        touch(agg);

        applyCaloriesInHighlights(user, agg);

        return dailyAggregateRepository.save(agg);
    }

    public DailyAggregate addSleep(
            String userId,
            Instant sleepEndAt,
            int totalMinutes,
            int deepMinutes,
            int remMinutes,
            int lightMinutes,
            int awakeMinutes
    ) {
        User user = requireUser(userId);
        ZoneId zoneId = userZone(user);

        LocalDate date = LocalDateTime.ofInstant(sleepEndAt, zoneId).toLocalDate();
        DailyAggregate agg = findOrCreate(userId, date);

        // MVP: cộng dồn để hỗ trợ nap hoặc nhiều session
        agg.setSleepMinutes(nvl(agg.getSleepMinutes()) + clamp0(totalMinutes));
        agg.setDeepMinutes(nvl(agg.getDeepMinutes()) + clamp0(deepMinutes));
        agg.setRemMinutes(nvl(agg.getRemMinutes()) + clamp0(remMinutes));
        agg.setLightMinutes(nvl(agg.getLightMinutes()) + clamp0(lightMinutes));
        agg.setAwakeMinutes(nvl(agg.getAwakeMinutes()) + clamp0(awakeMinutes));

        touch(agg);

        applySleepHighlights(user, agg);

        return dailyAggregateRepository.save(agg);
    }

    // ============================================================
    // Helpers: find/create + timezone
    // ============================================================

    private DailyAggregate findOrCreate(String userId, LocalDate date) {
        DailyAggregate agg = DailyAggregate.builder()
                .userId(userId)
                .date(date)
                // Default numeric fields to 0 for stability
                .steps(0)
                .distanceKm(0.0)
                .waterMl(0)
                .sleepMinutes(0)
                .deepMinutes(0)
                .remMinutes(0)
                .lightMinutes(0)
                .awakeMinutes(0)
                .caloriesIn(0)
                .caloriesOut(0)
                // Default collections to empty to avoid NPE
                .highlights(new java.util.ArrayList<>())
                .summary(null)
                .build();
        return dailyAggregateRepository.findByUserIdAndDate(userId, date)
                .orElseGet(() -> agg);
    }

    private void touch(DailyAggregate agg) {
        if (agg.getCreatedAt() == null) agg.setCreatedAt(Instant.now());
        agg.setUpdatedAt(Instant.now());
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private ZoneId userZone(User user) {
        String tz = DEFAULT_TZ;
        try {
            if (user.getSettings() != null && user.getSettings().getTimezone() != null) {
                tz = user.getSettings().getTimezone();
            }
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TZ);
        }
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private int clamp0(int v) {
        return Math.max(0, v);
    }

    // ============================================================
    // Highlights / Summary: WATER
    // ============================================================

    private void applyWaterHighlights(User user, DailyAggregate agg) {
        int water = nvl(agg.getWaterMl());

        int goal = DEFAULT_WATER_GOAL_ML;
        if (user.getGoals() != null && user.getGoals().getDailyWaterMl() != null) {
            goal = user.getGoals().getDailyWaterMl();
        }

        List<String> highlights = safeCopy(agg.getHighlights());

        // Remove old water highlights to avoid duplicates
        highlights.removeIf(s -> s != null && s.startsWith("Water:"));

        if (water >= goal) {
            highlights.add("Water: Reached daily water goal ✅");
        } else {
            int remaining = Math.max(0, goal - water);
            highlights.add("Water: Need " + remaining + "ml to reach goal");
        }

        agg.setHighlights(highlights);

        if (water >= goal) {
            setOrBlendSummary(agg, "Hôm nay bạn đã uống đủ nước. Tốt lắm!");
        } else {
            int remaining = Math.max(0, goal - water);
            setOrBlendSummary(agg, "Bạn cần uống thêm khoảng " + remaining + "ml nước để đạt mục tiêu.");
        }
    }

    // ============================================================
    // Highlights / Summary: SLEEP
    // ============================================================

    private void applySleepHighlights(User user, DailyAggregate agg) {
        int sleep = nvl(agg.getSleepMinutes());

        // MVP: chưa có sleep goal trong UserGoals => dùng default 7h
        int goalMin = DEFAULT_SLEEP_GOAL_MIN;

        List<String> highlights = safeCopy(agg.getHighlights());
        highlights.removeIf(s -> s != null && s.startsWith("Sleep:"));

        if (sleep >= goalMin) {
            highlights.add("Sleep: Reached 7h+ ✅");
            setOrBlendSummary(agg, "Giấc ngủ hôm nay khá tốt. Tiếp tục duy trì nhé!");
        } else if (sleep >= 360) { // 6h
            highlights.add("Sleep: Slightly low (under 7h)");
            setOrBlendSummary(agg, "Bạn ngủ hơi ít. Nếu có thể, hãy cố gắng ngủ thêm để hồi phục tốt hơn.");
        } else {
            highlights.add("Sleep: Too low (under 6h) ⚠️");
            setOrBlendSummary(agg, "Bạn ngủ quá ít. Cố gắng ngủ đủ giấc để cải thiện sức khỏe và năng lượng.");
        }

        agg.setHighlights(highlights);
    }

    // ============================================================
    // Highlights / Summary: CALORIES IN
    // ============================================================
    private void applyCaloriesInHighlights(User user, DailyAggregate agg) {
        int caloriesIn = nvl(agg.getCaloriesIn());

        int goal = DEFAULT_CALORIES_IN_GOAL;
        if (user.getGoals() != null && user.getGoals().getDailyCaloriesIn() != null && user.getGoals().getDailyCaloriesIn() > 0) {
            goal = user.getGoals().getDailyCaloriesIn();
        }

        List<String> highlights = safeCopy(agg.getHighlights());
        highlights.removeIf(s -> s != null && s.startsWith("Calories In:"));

        if (caloriesIn <= 0) {
            highlights.add("Calories In: No food logged");
            setOrBlendSummary(agg, "Hôm nay bạn chưa ghi nhận bữa ăn nào.");
        } else if (caloriesIn <= goal) {
            int remaining = Math.max(0, goal - caloriesIn);
            highlights.add("Calories In: " + caloriesIn + "/" + goal + " kcal ✅");
            if (remaining == 0) {
                setOrBlendSummary(agg, "Bạn đã đạt mục tiêu calories nạp vào hôm nay.");
            } else {
                setOrBlendSummary(agg, "Bạn còn khoảng " + remaining + " kcal để đạt mục tiêu calories nạp vào.");
            }
        } else {
            int exceeded = caloriesIn - goal;
            highlights.add("Calories In: Exceeded by " + exceeded + " kcal ⚠️");
            setOrBlendSummary(agg, "Bạn đã vượt mục tiêu calories nạp vào khoảng " + exceeded + " kcal. Cân nhắc điều chỉnh bữa ăn nhé.");
        }

        agg.setHighlights(highlights);
    }

    // ============================================================
    // Highlights / Summary: CALORIES OUT
    // ============================================================
    private void applyCaloriesOutHighlights(User user, DailyAggregate agg) {
        int caloriesOut = nvl(agg.getCaloriesOut());

        int goal = DEFAULT_CALORIES_OUT_GOAL;
        if (user.getGoals() != null && user.getGoals().getDailyCaloriesOut() != null && user.getGoals().getDailyCaloriesOut() > 0) {
            goal = user.getGoals().getDailyCaloriesOut();
        }

        List<String> highlights = safeCopy(agg.getHighlights());
        highlights.removeIf(s -> s != null && s.startsWith("Calories Out:"));

        if (caloriesOut >= goal) {
            highlights.add("Calories Out: Reached goal ✅");
            setOrBlendSummary(agg, "Bạn đã đạt mục tiêu vận động hôm nay. Tốt lắm!");
        } else {
            int remaining = Math.max(0, goal - caloriesOut);
            highlights.add("Calories Out: Need " + remaining + " kcal to reach goal");
            setOrBlendSummary(agg, "Bạn cần vận động thêm khoảng " + remaining + " kcal để đạt mục tiêu.");
        }

        agg.setHighlights(highlights);
    }

    // ============================================================
    // Summary blending (MVP)
    // ============================================================
    private void setOrBlendSummary(DailyAggregate agg, String newSentence) {
        if (newSentence == null || newSentence.isBlank()) return;

        String current = agg.getSummary();
        if (current == null || current.isBlank()) {
            agg.setSummary(newSentence);
            return;
        }

        // Avoid duplicate sentences
        if (current.contains(newSentence)) return;

        String sep = current.endsWith(".") ? " " : ". ";
        String blended = current + sep + newSentence;

        // MVP: keep summary reasonably short
        if (blended.length() > 400) {
            blended = blended.substring(0, 400);
        }

        agg.setSummary(blended);
    }

    private List<String> safeCopy(List<String> src) {
        return src == null ? new ArrayList<>() : new ArrayList<>(src);
    }

    public DailyAggregate addWorkout(String userId, Instant workoutEndAt, int steps, double distanceKm, int caloriesOut) {
        User user = requireUser(userId);
        ZoneId zoneId = userZone(user);

        LocalDate date = LocalDateTime.ofInstant(workoutEndAt, zoneId).toLocalDate();
        DailyAggregate agg = findOrCreate(userId, date);

        agg.setSteps(nvl(agg.getSteps()) + Math.max(0, steps));
        agg.setDistanceKm((agg.getDistanceKm() == null ? 0.0 : agg.getDistanceKm()) + Math.max(0.0, distanceKm));
        agg.setCaloriesOut(nvl(agg.getCaloriesOut()) + Math.max(0, caloriesOut));

        touch(agg);

        // (optional) simple highlight for workout
        List<String> highlights = safeCopy(agg.getHighlights());
        highlights.removeIf(s -> s != null && s.startsWith("Workout:"));
        highlights.add("Workout: +" + Math.max(0, steps) + " steps, +" + String.format("%.2f", Math.max(0.0, distanceKm)) + " km");
        agg.setHighlights(highlights);
        applyCaloriesOutHighlights(user, agg);

        return dailyAggregateRepository.save(agg);
    }
}