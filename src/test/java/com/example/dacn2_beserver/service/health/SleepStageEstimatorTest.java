package com.example.dacn2_beserver.service.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SleepStageEstimatorTest {

    private final SleepStageEstimator estimator = new SleepStageEstimator();

    @Test
    void stagesSumToTotal() {
        var s = estimator.estimate(480);
        assertThat(s.deep() + s.rem() + s.light() + s.awake()).isEqualTo(480);
    }

    @Test
    void usesExpectedRatios() {
        var s = estimator.estimate(480); // 13/22/60/5 %
        assertThat(s.deep()).isEqualTo(62);   // round(480*0.13)=62
        assertThat(s.rem()).isEqualTo(106);   // round(480*0.22)=106
        assertThat(s.awake()).isEqualTo(24);  // round(480*0.05)=24
        assertThat(s.light()).isEqualTo(288); // remainder 480-62-106-24
    }

    @Test
    void zeroAndNegativeClampToAllZero() {
        var z = estimator.estimate(0);
        assertThat(z.deep() + z.rem() + z.light() + z.awake()).isEqualTo(0);
        var n = estimator.estimate(-50);
        assertThat(n.deep() + n.rem() + n.light() + n.awake()).isEqualTo(0);
    }

    @Test
    void shortDurationStillSumsToTotal() {
        var s = estimator.estimate(30);
        assertThat(s.deep() + s.rem() + s.light() + s.awake()).isEqualTo(30);
        assertThat(s.light()).isGreaterThanOrEqualTo(0);
    }
}
