package com.example.dacn2_beserver.service.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SleepScorerTest {

    private final SleepScorer scorer = new SleepScorer();
    private static final int GOAL = 480;

    @Test
    void healthyBandScoresFull() {
        assertThat(scorer.score(420, GOAL).score()).isEqualTo(100); // 7h
        assertThat(scorer.score(480, GOAL).score()).isEqualTo(100); // 8h
        assertThat(scorer.score(540, GOAL).score()).isEqualTo(100); // 9h
    }

    @Test
    void zeroSleepScoresZero() {
        assertThat(scorer.score(0, GOAL).score()).isEqualTo(0);
    }

    @Test
    void halfwayBelowBandScalesLinearly() {
        // 210 min = half of 420 => 50
        assertThat(scorer.score(210, GOAL).score()).isEqualTo(50);
    }

    @Test
    void oversleepDecaysButFloors() {
        assertThat(scorer.score(660, GOAL).score()).isEqualTo(50);  // 11h
        assertThat(scorer.score(900, GOAL).score()).isEqualTo(40);  // 15h -> floor 40
    }

    @Test
    void qualityLabels() {
        assertThat(scorer.score(480, GOAL).quality()).isEqualTo("Tuyệt vời"); // 100
        assertThat(scorer.score(0, GOAL).quality()).isEqualTo("Cần cải thiện"); // 0
    }
}
