package com.example.dacn2_beserver.service.health;

import org.springframework.stereotype.Component;

/**
 * Chấm điểm giấc ngủ 0..100 dựa trên thời lượng so với vùng lành mạnh 7-9h.
 * Thuần, không I/O. Dùng goalMinutes làm tham chiếu hiển thị; vùng đầy điểm cố định 420..540.
 */
@Component
public class SleepScorer {

    private static final int BAND_LOW = 420;   // 7h
    private static final int BAND_HIGH = 540;   // 9h
    private static final int OVER_FLOOR_MIN = 660; // 11h -> 50
    private static final int OVER_FLOOR_SCORE = 50;
    private static final int HARD_FLOOR = 40;

    public record Result(int score, String quality) {}

    public Result score(int totalMinutes, int goalMinutes) {
        int total = Math.max(0, totalMinutes);
        int score;
        if (total >= BAND_LOW && total <= BAND_HIGH) {
            score = 100;
        } else if (total < BAND_LOW) {
            score = (int) Math.round((double) total / BAND_LOW * 100.0);
        } else {
            // decay from 100 at BAND_HIGH to 50 at OVER_FLOOR_MIN, then floor 40
            if (total > OVER_FLOOR_MIN) { // strict >: at exactly 660 the decay formula already yields 50; >= would wrongly floor it to 40
                score = HARD_FLOOR;
            } else {
                double t = (double) (total - BAND_HIGH) / (OVER_FLOOR_MIN - BAND_HIGH);
                score = (int) Math.round(100 - t * (100 - OVER_FLOOR_SCORE));
            }
        }
        score = Math.max(0, Math.min(100, score));
        return new Result(score, quality(score));
    }

    private String quality(int score) {
        if (score >= 85) return "Tuyệt vời";
        if (score >= 70) return "Tốt";
        if (score >= 50) return "Trung bình";
        return "Cần cải thiện";
    }
}
