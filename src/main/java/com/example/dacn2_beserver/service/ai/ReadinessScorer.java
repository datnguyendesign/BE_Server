package com.example.dacn2_beserver.service.ai;

import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chấm điểm sẵn sàng (readiness) thuần: so chỉ số trong ngày với mục tiêu cá nhân.
 * Không I/O, không phụ thuộc trạng thái — dễ test độc lập.
 */
@Component
public class ReadinessScorer {

    private static final DecimalFormatSymbols DOT_SYMBOLS = buildDotSymbols();
    private static final DecimalFormat INT_FMT = new DecimalFormat("#,##0", DOT_SYMBOLS);
    private static final DecimalFormat ONE_DP_FMT = new DecimalFormat("0.0", DOT_SYMBOLS);

    private static DecimalFormatSymbols buildDotSymbols() {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.ROOT);
        s.setGroupingSeparator('.');
        s.setDecimalSeparator('.');
        return s;
    }

    private static final int BASE = 60;
    private static final int MAX_STEPS = 15;
    private static final int MAX_WATER = 10;
    private static final int MAX_CALORIES = 5;
    private static final int MAX_SLEEP = 10;
    private static final int EMPTY_SCORE = 78;

    /** Mục tiêu đã resolve (luôn > 0). */
    public record Goals(int steps, int waterMl, int caloriesOut) {}

    /** Chỉ số trong ngày; field null nghĩa là không có dữ liệu. */
    public record DailyMetrics(Double steps, Double waterMl, Double caloriesOut, Double sleepHours) {
        boolean isEmpty() {
            return steps == null && waterMl == null && caloriesOut == null && sleepHours == null;
        }
    }

    public record ScoreResult(int score, List<String> actionPlan, String summary) {}

    public ScoreResult score(DailyMetrics m, Goals g, String dateLabel) {
        String date = (dateLabel == null || dateLabel.isBlank()) ? "hôm nay" : dateLabel;

        if (m.isEmpty()) {
            return new ScoreResult(
                    EMPTY_SCORE,
                    buildActionPlan(m, g),
                    "Phân tích ngày " + date + ": dữ liệu hiện tại đủ để đưa ra gợi ý sức khỏe cơ bản, "
                            + "hãy đồng bộ thiết bị thật để kết quả chính xác hơn.");
        }

        int score = BASE
                + ratioPoints(m.steps(), g.steps(), MAX_STEPS)
                + ratioPoints(m.waterMl(), g.waterMl(), MAX_WATER)
                + ratioPoints(m.caloriesOut(), g.caloriesOut(), MAX_CALORIES)
                + sleepPoints(m.sleepHours());
        score = Math.max(50, Math.min(95, score));

        return new ScoreResult(score, buildActionPlan(m, g), buildSummary(date, score, m));
    }

    private int ratioPoints(Double actual, int goal, int maxPoints) {
        if (actual == null || goal <= 0) {
            return 0;
        }
        double ratio = Math.min(actual / goal, 1.0);
        return (int) Math.round(ratio * maxPoints);
    }

    private int sleepPoints(Double hours) {
        if (hours == null) {
            return 0;
        }
        if (hours >= 7 && hours <= 9) {
            return MAX_SLEEP;
        }
        if (hours >= 6 && hours <= 10) {
            return MAX_SLEEP / 2;
        }
        return 2;
    }

    private String buildSummary(String date, int score, DailyMetrics m) {
        List<String> parts = new ArrayList<>();
        if (m.steps() != null) {
            parts.add(INT_FMT.format(m.steps().intValue()) + " bước");
        }
        if (m.waterMl() != null) {
            parts.add(INT_FMT.format(m.waterMl().intValue()) + "ml nước");
        }
        if (m.sleepHours() != null) {
            parts.add(ONE_DP_FMT.format(m.sleepHours()) + " giờ ngủ");
        }
        if (m.caloriesOut() != null) {
            parts.add(INT_FMT.format(m.caloriesOut().intValue()) + " kcal tiêu hao");
        }
        return "Phân tích ngày " + date + ": chỉ số sẵn sàng " + score + "/100 dựa trên "
                + String.join(", ", parts) + " so với mục tiêu cá nhân của bạn.";
    }

    private List<String> buildActionPlan(DailyMetrics m, Goals g) {
        List<String> plan = new ArrayList<>();

        if (m.steps() != null && m.steps() < g.steps()) {
            int remaining = (int) Math.round(g.steps() - m.steps());
            plan.add("Còn " + INT_FMT.format(remaining) + " bước nữa để đạt mục tiêu "
                    + INT_FMT.format(g.steps()) + " bước của bạn — thử đi bộ 10-15 phút sau bữa ăn.");
        } else if (m.steps() != null) {
            plan.add("Bạn đã đạt mục tiêu " + INT_FMT.format(g.steps())
                    + " bước hôm nay 👏 Duy trì vận động đều đặn.");
        } else {
            plan.add("Duy trì vận động đều và xen kẽ nghỉ ngơi hợp lý.");
        }

        if (m.waterMl() != null && m.waterMl() < g.waterMl()) {
            int remaining = (int) Math.round(g.waterMl() - m.waterMl());
            plan.add("Còn khoảng " + INT_FMT.format(remaining) + "ml nữa để đạt mục tiêu "
                    + INT_FMT.format(g.waterMl()) + "ml — uống rải đều theo từng khung giờ.");
        } else if (m.waterMl() != null) {
            plan.add("Bạn đã đủ nước hôm nay. Tiếp tục uống rải đều trong ngày.");
        } else {
            plan.add("Uống nước rải đều trong ngày thay vì uống dồn cuối ngày.");
        }

        if (m.sleepHours() != null && (m.sleepHours() < 7 || m.sleepHours() > 9)) {
            plan.add("Điều chỉnh giờ ngủ về 7-9 giờ và tránh caffeine trước khi ngủ.");
        } else {
            plan.add("Giữ lịch ngủ ổn định và tránh caffeine trước giờ ngủ.");
        }

        return plan;
    }
}
