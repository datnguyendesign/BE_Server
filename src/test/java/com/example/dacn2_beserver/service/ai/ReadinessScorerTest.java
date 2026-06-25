package com.example.dacn2_beserver.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessScorerTest {

    private final ReadinessScorer scorer = new ReadinessScorer();
    private final ReadinessScorer.Goals defaultGoals =
            new ReadinessScorer.Goals(8000, 2000, 500);

    @Test
    void emptyMetricsReturns78() {
        var m = new ReadinessScorer.DailyMetrics(null, null, null, null);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(78);
    }

    @Test
    void fullyMetGoalsScoresNearMax() {
        // base 60 + 15 + 10 + 5 + sleep(in 7-9 => 10) = 100, clamped to 95
        var m = new ReadinessScorer.DailyMetrics(8000.0, 2000.0, 500.0, 8.0);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(95);
    }

    @Test
    void exceedingGoalClampsRatioAtOne() {
        // double every metric; ratios cap at 1.0, so same as fully-met => 95
        var m = new ReadinessScorer.DailyMetrics(16000.0, 4000.0, 1000.0, 8.0);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(95);
    }

    @Test
    void halfStepsGivesHalfStepPoints() {
        // base 60 + round(0.5*15)=8 ; water/calo/sleep null => +0 ; = 68
        var m = new ReadinessScorer.DailyMetrics(4000.0, null, null, null);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(68);
    }

    @Test
    void sleepInIdealRangeAddsTen() {
        var m = new ReadinessScorer.DailyMetrics(null, null, null, 8.0);
        // base 60 + 10 = 70
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(70);
    }

    @Test
    void sleepInAcceptableRangeAddsFive() {
        var m = new ReadinessScorer.DailyMetrics(null, null, null, 6.5);
        // base 60 + 5 = 65
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(65);
    }

    @Test
    void sleepOutsideRangeAddsTwo() {
        var m = new ReadinessScorer.DailyMetrics(null, null, null, 4.0);
        // base 60 + 2 = 62
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.score()).isEqualTo(62);
    }

    @Test
    void personalGoalChangesStepPoints() {
        // smaller personal step goal => easier to fully meet
        var goals = new ReadinessScorer.Goals(2000, 2000, 500);
        var m = new ReadinessScorer.DailyMetrics(2000.0, null, null, null);
        // base 60 + 15 = 75
        var r = scorer.score(m, goals, "2026-06-25");
        assertThat(r.score()).isEqualTo(75);
    }

    @Test
    void actionPlanMentionsRemainingStepsWhenShort() {
        var m = new ReadinessScorer.DailyMetrics(6000.0, null, null, null);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.actionPlan()).anyMatch(s -> s.contains("2.000") && s.contains("8.000"));
    }

    @Test
    void actionPlanCongratulatesWhenStepsMet() {
        var m = new ReadinessScorer.DailyMetrics(9000.0, null, null, null);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.actionPlan()).anyMatch(s -> s.contains("đạt mục tiêu"));
    }

    @Test
    void summaryIncludesScoreAndDate() {
        var m = new ReadinessScorer.DailyMetrics(8000.0, 2000.0, 500.0, 8.0);
        var r = scorer.score(m, defaultGoals, "2026-06-25");
        assertThat(r.summary()).contains("2026-06-25").contains("95");
    }
}
