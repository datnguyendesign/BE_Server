package com.example.dacn2_beserver.service.ai;

import com.example.dacn2_beserver.dto.ai.AiFeedbackRequest;
import com.example.dacn2_beserver.dto.ai.DailyAnalysisRequest;
import com.example.dacn2_beserver.dto.ai.DailyAnalysisResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import com.example.dacn2_beserver.model.ai.AiFeedback;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.repository.AiFeedbackRepository;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Phân tích sức khỏe ngày (heuristic, chạy nội bộ — không phụ thuộc AI server),
 * cá nhân hoá theo mục tiêu (UserGoals) và dữ liệu thật (DailyAggregate).
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final String DISCLAIMER =
            "Thông tin AI chỉ để tham khảo sức khỏe tổng quát, không thay thế chẩn đoán hoặc điều trị y tế.";

    private static final int DEFAULT_STEPS_GOAL = 8000;
    private static final int DEFAULT_WATER_GOAL_ML = 2000;
    private static final int DEFAULT_CALORIES_OUT_GOAL = 500;

    private final AiFeedbackRepository aiFeedbackRepository;
    private final UserRepository userRepository;
    private final DailyAggregateRepository dailyAggregateRepository;
    private final ReadinessScorer readinessScorer;

    public DailyAnalysisResponse analyzeDaily(String userId, DailyAnalysisRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));

        LocalDate date = resolveDate(req.getDate(), user);
        ReadinessScorer.Goals goals = resolveGoals(user.getGoals());
        ReadinessScorer.DailyMetrics metrics = resolveMetrics(userId, date, req.getSummary());

        String dateLabel = req.getDate() != null && !req.getDate().isBlank() ? req.getDate() : date.toString();
        ReadinessScorer.ScoreResult result = readinessScorer.score(metrics, goals, dateLabel);

        return DailyAnalysisResponse.builder()
                .readinessScore(result.score())
                .summary(result.summary())
                .targetUsers(List.of(
                        "Người bận rộn muốn theo dõi sức khỏe hằng ngày trong một app.",
                        "Người kiểm soát cân nặng cần ước tính calories từ ảnh món ăn.",
                        "Người mới tập luyện cần gợi ý dễ hiểu và có cảnh báo an toàn."))
                .missingForHealthApp(List.of(
                        "Đồng bộ nhịp tim/giấc ngủ thật từ wearable hoặc HealthKit/Google Fit.",
                        "Nhật ký calories theo ngày và phản hồi độ chính xác AI.",
                        "Phân quyền dữ liệu y tế, chính sách bảo mật và cảnh báo cấp cứu rõ ràng."))
                .optimizations(List.of(
                        "Ưu tiên cache offline cho dashboard và nhật ký sức khỏe.",
                        "Hiển thị confidence, khẩu phần và macro khi nhận diện món ăn.",
                        "Thu thập feedback sau trải nghiệm để cải thiện model và UI."))
                .actionPlan(result.actionPlan())
                .disclaimer(DISCLAIMER)
                .build();
    }

    public void saveFeedback(String userId, AiFeedbackRequest req) {
        aiFeedbackRepository.save(AiFeedback.builder()
                .userId(userId)
                .rating(req.getRating())
                .comment(req.getComment())
                .context(req.getContext())
                .selectedDate(req.getSelectedDate())
                .build());
    }

    // ---------- helpers ----------

    private LocalDate resolveDate(String raw, User user) {
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDate.parse(raw.trim());
            } catch (Exception ignored) {
                // fall through to today
            }
        }
        return LocalDate.now(userZone(user));
    }

    private ZoneId userZone(User user) {
        try {
            if (user.getSettings() != null && user.getSettings().getTimezone() != null) {
                return ZoneId.of(user.getSettings().getTimezone());
            }
        } catch (Exception ignored) {
        }
        return ZoneId.of("UTC");
    }

    private ReadinessScorer.Goals resolveGoals(UserGoals g) {
        int steps = positiveOr(g == null ? null : g.getDailySteps(), DEFAULT_STEPS_GOAL);
        int water = positiveOr(g == null ? null : g.getDailyWaterMl(), DEFAULT_WATER_GOAL_ML);
        int calo = positiveOr(g == null ? null : g.getDailyCaloriesOut(), DEFAULT_CALORIES_OUT_GOAL);
        return new ReadinessScorer.Goals(steps, water, calo);
    }

    private int positiveOr(Integer value, int fallback) {
        return (value != null && value > 0) ? value : fallback;
    }

    private ReadinessScorer.DailyMetrics resolveMetrics(String userId, LocalDate date, Object summary) {
        DailyAggregate agg = dailyAggregateRepository.findByUserIdAndDate(userId, date).orElse(null);
        if (agg != null) {
            return new ReadinessScorer.DailyMetrics(
                    toDouble(agg.getSteps()),
                    toDouble(agg.getWaterMl()),
                    toDouble(agg.getCaloriesOut()),
                    agg.getSleepMinutes() != null ? agg.getSleepMinutes() / 60.0 : null);
        }
        // Backward-compat: parse client-provided summary if no aggregate exists.
        return extractFromSummary(summary);
    }

    private Double toDouble(Integer v) {
        return v == null ? null : v.doubleValue();
    }

    private ReadinessScorer.DailyMetrics extractFromSummary(Object summary) {
        if (!(summary instanceof Map<?, ?> map)) {
            return new ReadinessScorer.DailyMetrics(null, null, null, null);
        }
        Double[] acc = new Double[4]; // steps, water, sleepHours, caloriesOut
        walk(map, acc);
        return new ReadinessScorer.DailyMetrics(acc[0], acc[1], acc[3], acc[2]);
    }

    private void walk(Map<?, ?> map, Double[] acc) {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey()).toLowerCase();
            Object val = e.getValue();
            if (val instanceof Map<?, ?> nested) {
                walk(nested, acc);
                continue;
            }
            Double num = parseDouble(val);
            if (num == null) {
                continue;
            }
            if (key.contains("step")) {
                acc[0] = max(acc[0], num);
            } else if (key.contains("water")) {
                acc[1] = max(acc[1], num);
            } else if (key.contains("sleep")) {
                acc[2] = max(acc[2], num);
            } else if (key.contains("calorie") || key.contains("kcal")) {
                acc[3] = max(acc[3], num);
            }
        }
    }

    private Double max(Double a, Double b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : Math.max(a, b);
    }

    private Double parseDouble(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
