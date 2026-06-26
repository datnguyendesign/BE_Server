# Sleep Tracking — Real Data + Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mobile Sleep screen's mock data with real backend data and let users log sleep via a live timer and a manual form, with the sleep score and stage estimation owned by the backend.

**Architecture:** Backend-owned (Approach A). Two new pure components (`SleepStageEstimator`, `SleepScorer`) compute stage splits and a 0–100 score; `SleepService` fills estimated stages on manual logs and guards against future entries; `DailyAggregateService.addSleep` switches to replace-on-same-day and stores `sleepScore`. The score field is surfaced through the existing `/health/summary` endpoints. The React Native app fetches and renders this data and adds a logging modal (timer + manual form). No new endpoints.

**Tech Stack:** Spring Boot 3.5 / Java 17 / Maven / MongoDB (backend); React Native 0.82 / React 19 / TypeScript / Axios / Jest (frontend).

## Global Constraints

- Sleep goal is fixed and unified at **480 minutes (8h)** everywhere — score, progress, highlights, home card, calendar.
- Backend routes have **no `/api` prefix**; sleep logging uses existing `POST /health/sleep`.
- API responses are wrapped in `{ data: ... }`; FE strips with `unwrapApiData()`.
- Stage estimation must round so deep + rem + light + awake == totalMinutes exactly.
- Estimated stages must be flagged so the FE can label them "estimated".
- Commits follow Conventional Commits.
- Backend tests: run with `./mvnw test -Dtest=ClassName` (Maven wrapper). FE tests: `npm test -- --testPathPattern=<file>`.
- Two separate git repos: backend changes commit in `DACN2_BEserver`, frontend changes commit in `DACN2_FEserver`. Never stage across both.

---

## File Structure

**Backend (`DACN2_BEserver`)**
- Create `src/main/java/com/example/dacn2_beserver/service/health/SleepStageEstimator.java` — pure duration→stage split.
- Create `src/main/java/com/example/dacn2_beserver/service/health/SleepScorer.java` — pure 0–100 score + quality label.
- Modify `model/health/DailyAggregate.java` — add `Integer sleepScore`.
- Modify `dto/health/DailyAggregateResponse.java` — add `Integer sleepScore`.
- Modify `dto/health/CalendarDaySummaryResponse.java` — add `int sleepScore`.
- Modify `service/health/DailyAggregateService.java` — 8h goal constant; `addSleep` replace-mode + score; highlight thresholds.
- Modify `service/health/SleepService.java` — estimate stages when no segments; future-entry guard; pass replace flag.
- Modify `service/health/SummaryService.java` — map `sleepScore`.
- Modify `service/health/CalendarService.java` — map `sleepScore` (real + empty days).
- Create tests under `src/test/java/com/example/dacn2_beserver/service/health/`.

**Frontend (`DACN2_FEserver`)**
- Modify `src/types/home.ts` — extend `DailyMetrics` with stage + score fields.
- Create `src/services/sleepService.ts` — `createSleepSession` + `fetchSleepWeek`.
- Create `src/screens/SleepTracking/useSleepData.ts` — hook mapping aggregate → screen props.
- Modify `src/screens/SleepTracking/SleepTrackingScreen.tsx` — use real data + empty state + log entry point.
- Create `src/screens/SleepTracking/LogSleepModal.tsx` — timer + manual form.
- Modify `src/context/SleepContext.tsx` — remove mock constants (keep WEEK_LABELS/WEEK_MAX_HOURS).
- Modify `src/components/Home/HeartSleepGrid/HeartSleepGrid.tsx` — unify 8h target/status.
- Modify `src/components/Calendar/DailySummary/DailySummary.tsx` + `src/utils/healthStatus.ts` — unify 8h status.

---

## Task 1: SleepStageEstimator (backend, pure)

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/SleepStageEstimator.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/SleepStageEstimatorTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `SleepStageEstimator.Stages estimate(int totalMinutes)` where
  `record Stages(int deep, int rem, int light, int awake)`. Guarantee: `deep + rem + light + awake == max(0, totalMinutes)`. Ratios: deep 13%, rem 22%, light 60%, awake 5%; `light` absorbs the rounding remainder.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=SleepStageEstimatorTest`
Expected: FAIL — `SleepStageEstimator` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.dacn2_beserver.service.health;

import org.springframework.stereotype.Component;

/**
 * Ước lượng phân bổ giai đoạn ngủ từ tổng thời lượng (khi không có dữ liệu segment thật).
 * Thuần, không I/O. Tỷ lệ người lớn điển hình: deep 13%, REM 22%, light 60%, awake 5%.
 * light hấp thụ phần dư làm tròn để 4 giá trị cộng đúng bằng total.
 */
@Component
public class SleepStageEstimator {

    public record Stages(int deep, int rem, int light, int awake) {}

    public Stages estimate(int totalMinutes) {
        int total = Math.max(0, totalMinutes);
        int deep = (int) Math.round(total * 0.13);
        int rem = (int) Math.round(total * 0.22);
        int awake = (int) Math.round(total * 0.05);
        int light = total - deep - rem - awake;
        if (light < 0) light = 0;
        return new Stages(deep, rem, light, awake);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=SleepStageEstimatorTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/SleepStageEstimator.java src/test/java/com/example/dacn2_beserver/service/health/SleepStageEstimatorTest.java
git commit -m "feat(health): add SleepStageEstimator for duration-based stage split"
```

---

## Task 2: SleepScorer (backend, pure)

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/SleepScorer.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/SleepScorerTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `SleepScorer.Result score(int totalMinutes, int goalMinutes)` where
  `record Result(int score, String quality)`. `score` in `[0,100]`. Curve: full 100 in the healthy band 420–540 min (7–9h); below 420 scales linearly from 0 at 0 min to 100 at 420; above 540 decays linearly to 50 at 660 min (11h) and floors at 40. `quality` (Vietnamese): >=85 "Tuyệt vời", >=70 "Tốt", >=50 "Trung bình", else "Cần cải thiện".

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=SleepScorerTest`
Expected: FAIL — `SleepScorer` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
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
            if (total >= OVER_FLOOR_MIN) {
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=SleepScorerTest`
Expected: PASS (5 tests). Note: `score(660,...)` hits the `>= OVER_FLOOR_MIN` branch → 50; `score(900,...)` → 40.

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/SleepScorer.java src/test/java/com/example/dacn2_beserver/service/health/SleepScorerTest.java
git commit -m "feat(health): add SleepScorer (0-100 duration-vs-band score)"
```

---

## Task 3: Add sleepScore to model + response DTOs

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/model/health/DailyAggregate.java:49`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/DailyAggregateResponse.java:35`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/CalendarDaySummaryResponse.java:29`

**Interfaces:**
- Produces: `DailyAggregate.getSleepScore()/setSleepScore(Integer)`; `DailyAggregateResponse` builder `.sleepScore(Integer)`; `CalendarDaySummaryResponse` builder `.sleepScore(int)`.

This task is a pure field addition; it has no standalone behavioral test. It is verified by compilation here and exercised by Tasks 4–5 tests. Keep it its own commit so the field addition is reviewable in isolation.

- [ ] **Step 1: Add field to `DailyAggregate`**

In `DailyAggregate.java`, after line 49 (`private Integer awakeMinutes;`) add:

```java
    private Integer sleepScore;
```

- [ ] **Step 2: Add field to `DailyAggregateResponse`**

In `DailyAggregateResponse.java`, after line 35 (`private Integer awakeMinutes;`) add:

```java
    private Integer sleepScore;
```

- [ ] **Step 3: Add field to `CalendarDaySummaryResponse`**

In `CalendarDaySummaryResponse.java`, after line 29 (`private int awakeMinutes;`) add:

```java
    private int sleepScore;
```

- [ ] **Step 4: Verify it compiles**

Run: `cd DACN2_BEserver && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/model/health/DailyAggregate.java src/main/java/com/example/dacn2_beserver/dto/health/DailyAggregateResponse.java src/main/java/com/example/dacn2_beserver/dto/health/CalendarDaySummaryResponse.java
git commit -m "feat(health): add sleepScore field to aggregate + response DTOs"
```

---

## Task 4: DailyAggregateService — 8h goal, replace-mode, score

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/DailyAggregateService.java:40` (goal constant), `:131-158` (`addSleep`), `:254-275` (`applySleepHighlights`)
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/DailyAggregateServiceSleepTest.java`

**Interfaces:**
- Consumes: `SleepScorer.score(int,int)` (Task 2).
- Produces: new overload
  `DailyAggregate addSleep(String userId, Instant sleepEndAt, int totalMinutes, int deep, int rem, int light, int awake, boolean replace)`.
  When `replace==true`, sleep + stage fields are SET to the given values; when `false`, they are ADDED (existing behavior). After updating fields, `sleepScore` is set from `SleepScorer.score(sleepMinutes, 480)`. The old 7-arg signature is kept and delegates with `replace=false` for backward compatibility.

- [ ] **Step 1: Write the failing test**

```java
package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DailyAggregateServiceSleepTest {

    private DailyAggregateRepository aggRepo;
    private UserRepository userRepo;
    private DailyAggregateService service;

    @BeforeEach
    void setUp() {
        aggRepo = mock(DailyAggregateRepository.class);
        userRepo = mock(UserRepository.class);
        HealthEventRawRepository rawRepo = mock(HealthEventRawRepository.class);
        service = new DailyAggregateService(aggRepo, userRepo, rawRepo, new SleepScorer());

        when(userRepo.findById(anyString())).thenReturn(Optional.of(new User()));
        when(aggRepo.findByUserIdAndDate(anyString(), any())).thenReturn(Optional.empty());
        when(aggRepo.save(any(DailyAggregate.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void replaceModeSetsRatherThanAdds() {
        Instant wake = Instant.parse("2026-06-26T07:00:00Z");
        // first log 480 with replace
        DailyAggregate first = service.addSleep("u1", wake, 480, 60, 100, 300, 20, true);
        assertThat(first.getSleepMinutes()).isEqualTo(480);

        // existing day now returns 480; a second replace of 300 should SET to 300, not 780
        when(aggRepo.findByUserIdAndDate(anyString(), any())).thenReturn(Optional.of(first));
        DailyAggregate second = service.addSleep("u1", wake, 300, 40, 60, 190, 10, true);
        assertThat(second.getSleepMinutes()).isEqualTo(300);
    }

    @Test
    void cumulativeModeStillAdds() {
        Instant wake = Instant.parse("2026-06-26T07:00:00Z");
        DailyAggregate first = service.addSleep("u1", wake, 100, 0, 0, 100, 0, false);
        when(aggRepo.findByUserIdAndDate(anyString(), any())).thenReturn(Optional.of(first));
        DailyAggregate second = service.addSleep("u1", wake, 50, 0, 0, 50, 0, false);
        assertThat(second.getSleepMinutes()).isEqualTo(150);
    }

    @Test
    void sleepScoreIsComputed() {
        Instant wake = Instant.parse("2026-06-26T07:00:00Z");
        DailyAggregate agg = service.addSleep("u1", wake, 480, 60, 100, 300, 20, true);
        assertThat(agg.getSleepScore()).isEqualTo(100); // 8h -> full
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=DailyAggregateServiceSleepTest`
Expected: FAIL — 4-field constructor with `SleepScorer` and 8-arg `addSleep` do not exist.

- [ ] **Step 3: Implement the changes**

3a. Add the `SleepScorer` dependency. In `DailyAggregateService.java`, after line 45 (`private final HealthEventRawRepository healthEventRawRepository;`) add:

```java
    private final SleepScorer sleepScorer;
```

(`@RequiredArgsConstructor` will include it; the test constructs it explicitly with 4 args in the order: aggRepo, userRepo, rawRepo, sleepScorer. Confirm the field order in the class matches that order — `dailyAggregateRepository`, `userRepository`, `healthEventRawRepository`, then `sleepScorer`.)

3b. Change the goal constant on line 40 from:

```java
    private static final int DEFAULT_SLEEP_GOAL_MIN = 420; // 7h
```

to:

```java
    private static final int DEFAULT_SLEEP_GOAL_MIN = 480; // 8h
```

3c. Replace the `addSleep` method (lines 131–158) with the keep-old-signature + new-overload version:

```java
    public DailyAggregate addSleep(
            String userId,
            Instant sleepEndAt,
            int totalMinutes,
            int deepMinutes,
            int remMinutes,
            int lightMinutes,
            int awakeMinutes
    ) {
        return addSleep(userId, sleepEndAt, totalMinutes, deepMinutes, remMinutes,
                lightMinutes, awakeMinutes, false);
    }

    public DailyAggregate addSleep(
            String userId,
            Instant sleepEndAt,
            int totalMinutes,
            int deepMinutes,
            int remMinutes,
            int lightMinutes,
            int awakeMinutes,
            boolean replace
    ) {
        User user = requireUser(userId);
        ZoneId zoneId = userZone(user);

        LocalDate date = LocalDateTime.ofInstant(sleepEndAt, zoneId).toLocalDate();
        DailyAggregate agg = findOrCreate(userId, date);

        if (replace) {
            agg.setSleepMinutes(clamp0(totalMinutes));
            agg.setDeepMinutes(clamp0(deepMinutes));
            agg.setRemMinutes(clamp0(remMinutes));
            agg.setLightMinutes(clamp0(lightMinutes));
            agg.setAwakeMinutes(clamp0(awakeMinutes));
        } else {
            agg.setSleepMinutes(nvl(agg.getSleepMinutes()) + clamp0(totalMinutes));
            agg.setDeepMinutes(nvl(agg.getDeepMinutes()) + clamp0(deepMinutes));
            agg.setRemMinutes(nvl(agg.getRemMinutes()) + clamp0(remMinutes));
            agg.setLightMinutes(nvl(agg.getLightMinutes()) + clamp0(lightMinutes));
            agg.setAwakeMinutes(nvl(agg.getAwakeMinutes()) + clamp0(awakeMinutes));
        }

        agg.setSleepScore(sleepScorer.score(nvl(agg.getSleepMinutes()), DEFAULT_SLEEP_GOAL_MIN).score());

        touch(agg);

        applySleepHighlights(user, agg);

        return dailyAggregateRepository.save(agg);
    }
```

3d. Update `applySleepHighlights` (lines 254–275) thresholds/messages to the 8h goal. Replace the body from the `if (sleep >= goalMin)` block with:

```java
        if (sleep >= goalMin) { // 8h
            highlights.add("Sleep: Reached 8h+ ✅");
            setOrBlendSummary(agg, "Giấc ngủ hôm nay khá tốt. Tiếp tục duy trì nhé!");
        } else if (sleep >= 420) { // 7h
            highlights.add("Sleep: Slightly low (under 8h)");
            setOrBlendSummary(agg, "Bạn ngủ hơi ít. Nếu có thể, hãy cố gắng ngủ thêm để hồi phục tốt hơn.");
        } else if (sleep >= 360) { // 6h
            highlights.add("Sleep: Low (under 7h)");
            setOrBlendSummary(agg, "Bạn ngủ hơi ít. Nếu có thể, hãy cố gắng ngủ thêm để hồi phục tốt hơn.");
        } else {
            highlights.add("Sleep: Too low (under 6h) ⚠️");
            setOrBlendSummary(agg, "Bạn ngủ quá ít. Cố gắng ngủ đủ giấc để cải thiện sức khỏe và năng lượng.");
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=DailyAggregateServiceSleepTest`
Expected: PASS (3 tests). If the constructor arg order differs, adjust the test's `new DailyAggregateService(...)` call to match the actual field declaration order.

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/DailyAggregateService.java src/test/java/com/example/dacn2_beserver/service/health/DailyAggregateServiceSleepTest.java
git commit -m "feat(health): unify 8h sleep goal, add replace-mode + sleepScore to addSleep"
```

---

## Task 5: SleepService — estimate stages + future guard

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/SleepService.java:22-66`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/SleepServiceTest.java`

**Interfaces:**
- Consumes: `SleepStageEstimator.estimate(int)` (Task 1); `DailyAggregateService.addSleep(...,boolean)` (Task 4).
- Produces: unchanged public method `create(String, CreateSleepSessionRequest)`. New behavior: when `req.getSegments()` is null/empty, stage minutes come from the estimator and the session source is set to the request's source (default `"manual"`); future `endAt` (after `Instant.now()`) throws `IllegalArgumentException`. Aggregation is called with `replace=true` when stages were estimated (manual/timer logs), `replace=false` when real segments were provided.

- [ ] **Step 1: Write the failing test**

```java
package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateSleepSessionRequest;
import com.example.dacn2_beserver.dto.health.SleepSessionResponse;
import com.example.dacn2_beserver.dto.health.TimeRangeDto;
import com.example.dacn2_beserver.model.health.SleepSession;
import com.example.dacn2_beserver.repository.SleepSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SleepServiceTest {

    private SleepSessionRepository repo;
    private DailyAggregateService aggService;
    private SleepService service;

    @BeforeEach
    void setUp() {
        repo = mock(SleepSessionRepository.class);
        aggService = mock(DailyAggregateService.class);
        service = new SleepService(repo, aggService, new SleepStageEstimator());
        when(repo.save(any(SleepSession.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateSleepSessionRequest req(Instant start, Instant end) {
        return CreateSleepSessionRequest.builder()
                .time(TimeRangeDto.builder().startAt(start).endAt(end).build())
                .build();
    }

    @Test
    void manualLogEstimatesStagesAndReplaces() {
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant start = end.minus(8, ChronoUnit.HOURS); // 480 min
        SleepSessionResponse res = service.create("u1", req(start, end));

        assertThat(res.getTotalMinutes()).isEqualTo(480);
        assertThat(res.getDeepMinutes() + res.getRemMinutes()
                + res.getLightMinutes() + res.getAwakeMinutes()).isEqualTo(480);
        verify(aggService).addSleep(eq("u1"), eq(end), eq(480),
                anyInt(), anyInt(), anyInt(), anyInt(), eq(true));
    }

    @Test
    void futureEndRejected() {
        Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant start = end.minus(8, ChronoUnit.HOURS);
        assertThatThrownBy(() -> service.create("u1", req(start, end)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=SleepServiceTest`
Expected: FAIL — 3-arg constructor and estimate/replace behavior do not exist.

- [ ] **Step 3: Implement the changes**

3a. Add the estimator dependency. After line 25 (`private final DailyAggregateService dailyAggregateService;`) add:

```java
    private final SleepStageEstimator sleepStageEstimator;
```

3b. In `create(...)`, after the time-range validation (after line 33) add the future guard:

```java
        if (endAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Sleep cannot end in the future");
        }
```

3c. Replace lines 36–42 (stage computation) with estimate-when-empty logic and a `replace` flag:

```java
        int totalMinutes = (int) Duration.between(startAt, endAt).toMinutes();

        boolean hasRealSegments = req.getSegments() != null && !req.getSegments().isEmpty();
        int deep, rem, light, awake;
        if (hasRealSegments) {
            Map<SleepStage, Integer> stageMinutes = computeStageMinutes(req.getSegments());
            deep = stageMinutes.getOrDefault(SleepStage.DEEP, 0);
            rem = stageMinutes.getOrDefault(SleepStage.REM, 0);
            light = stageMinutes.getOrDefault(SleepStage.LIGHT, 0);
            awake = stageMinutes.getOrDefault(SleepStage.AWAKE, 0);
        } else {
            SleepStageEstimator.Stages est = sleepStageEstimator.estimate(totalMinutes);
            deep = est.deep();
            rem = est.rem();
            light = est.light();
            awake = est.awake();
        }
        boolean replace = !hasRealSegments;
```

3d. Update the aggregation call on line 63 to pass `replace`:

```java
        dailyAggregateService.addSleep(userId, endAt, totalMinutes, deep, rem, light, awake, replace);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=SleepServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/SleepService.java src/test/java/com/example/dacn2_beserver/service/health/SleepServiceTest.java
git commit -m "feat(health): estimate stages on manual sleep logs + reject future entries"
```

---

## Task 6: Map sleepScore in Summary + Calendar responses

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/SummaryService.java:31-50`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/CalendarService.java:47-98`

**Interfaces:**
- Consumes: `DailyAggregate.getSleepScore()` (Task 3); response builders `.sleepScore(...)` (Task 3).
- Produces: `/health/summary/{date}`, `/health/summary?from&to`, and `/health/calendar` responses now include `sleepScore`.

This is a wiring change verified by compilation + the existing app-context test. No new unit test (the mapping is a one-line passthrough mirrored on the existing `sleepMinutes` line and covered indirectly by Task 4's score computation).

- [ ] **Step 1: Map score in `SummaryService.toResponse`**

In `SummaryService.java`, after line 41 (`.awakeMinutes(nvl(agg.getAwakeMinutes()))`) add:

```java
                .sleepScore(nvl(agg.getSleepScore()))
```

- [ ] **Step 2: Map score in `CalendarService` real-day branch**

In `CalendarService.java`, after line 58 (`.awakeMinutes(nvl(a.getAwakeMinutes()))`) add:

```java
                .sleepScore(nvl(a.getSleepScore()))
```

- [ ] **Step 3: Map score in `CalendarService.emptyDay`**

In `CalendarService.java`, after line 90 (`.awakeMinutes(0)`) add:

```java
                .sleepScore(0)
```

- [ ] **Step 4: Compile + run the full backend test suite**

Run: `cd DACN2_BEserver && ./mvnw test`
Expected: BUILD SUCCESS, all tests pass (including Tasks 1–5).

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/SummaryService.java src/main/java/com/example/dacn2_beserver/service/health/CalendarService.java
git commit -m "feat(health): surface sleepScore in summary and calendar responses"
```

---

## Task 7: FE types + sleep service

**Files:**
- Modify: `DACN2_FEserver/src/types/home.ts:1-8`
- Create: `DACN2_FEserver/src/services/sleepService.ts`
- Test: `DACN2_FEserver/src/services/__tests__/sleepService.test.ts`

**Interfaces:**
- Consumes: existing `api`, `unwrapApiData` from `src/services/api.ts`.
- Produces:
  - `DailyMetrics` extended with `deepMinutes: number | null; remMinutes: number | null; lightMinutes: number | null; sleepScore: number | null;`
  - `createSleepSession(input: { startAt: string; endAt: string; source?: string }): Promise<boolean>` — POSTs `/health/sleep` with `{ time: { startAt, endAt }, meta: { source } }`; returns `true` on success, `false` on failure.
  - `fetchSleepWeek(from: string, to: string): Promise<DailyMetrics[]>` — GET `/health/summary?from&to`, returns `[]` on failure.

- [ ] **Step 1: Extend the `DailyMetrics` type**

In `home.ts`, replace lines 1–8 with:

```ts
export interface DailyMetrics {
  date: string;
  steps: number | null;
  caloriesOut: number | null;
  avgHeartRate: number | null;
  sleepMinutes: number | null;
  deepMinutes: number | null;
  remMinutes: number | null;
  lightMinutes: number | null;
  sleepScore: number | null;
  waterMl: number | null;
}
```

- [ ] **Step 2: Write the failing test**

```ts
import { createSleepSession } from '../sleepService';
import { api } from '../api';

jest.mock('../api', () => ({
  api: { post: jest.fn() },
  unwrapApiData: (x: any) => x?.data ?? x,
}));

describe('createSleepSession', () => {
  beforeEach(() => jest.clearAllMocks());

  it('posts to /health/sleep with time + meta and returns true', async () => {
    (api.post as jest.Mock).mockResolvedValue({ data: { data: {} } });
    const ok = await createSleepSession({
      startAt: '2026-06-25T23:00:00.000Z',
      endAt: '2026-06-26T07:00:00.000Z',
      source: 'manual',
    });
    expect(ok).toBe(true);
    expect(api.post).toHaveBeenCalledWith('/health/sleep', {
      time: {
        startAt: '2026-06-25T23:00:00.000Z',
        endAt: '2026-06-26T07:00:00.000Z',
      },
      meta: { source: 'manual' },
    });
  });

  it('returns false on error', async () => {
    (api.post as jest.Mock).mockRejectedValue(new Error('boom'));
    const ok = await createSleepSession({
      startAt: '2026-06-25T23:00:00.000Z',
      endAt: '2026-06-26T07:00:00.000Z',
    });
    expect(ok).toBe(false);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=sleepService`
Expected: FAIL — `sleepService` module not found.

- [ ] **Step 4: Implement `sleepService.ts`**

```ts
import { api, unwrapApiData } from './api';
import type { DailyMetrics } from '../types/home';

export interface CreateSleepInput {
  startAt: string; // ISO
  endAt: string; // ISO
  source?: string;
}

export const createSleepSession = async (
  input: CreateSleepInput,
): Promise<boolean> => {
  try {
    await api.post('/health/sleep', {
      time: { startAt: input.startAt, endAt: input.endAt },
      meta: { source: input.source ?? 'manual' },
    });
    return true;
  } catch {
    return false;
  }
};

export const fetchSleepWeek = async (
  from: string,
  to: string,
): Promise<DailyMetrics[]> => {
  try {
    const res = await api.get('/health/summary', { params: { from, to } });
    return (unwrapApiData(res.data) as DailyMetrics[]) ?? [];
  } catch {
    return [];
  }
};
```

- [ ] **Step 5: Run test to verify it passes + commit**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=sleepService`
Expected: PASS (2 tests).

```bash
cd DACN2_FEserver
git add src/types/home.ts src/services/sleepService.ts src/services/__tests__/sleepService.test.ts
git commit -m "feat(sleep): add sleep service + extend DailyMetrics with stages/score"
```

---

## Task 8: useSleepData hook (map aggregate → screen props)

**Files:**
- Create: `DACN2_FEserver/src/screens/SleepTracking/useSleepData.ts`
- Create: `DACN2_FEserver/src/screens/SleepTracking/sleepFormat.ts` (pure helpers)
- Test: `DACN2_FEserver/src/screens/SleepTracking/__tests__/sleepFormat.test.ts`

**Interfaces:**
- Consumes: `fetchDayAggregate` (`src/services/calendarService.ts`), `fetchSleepWeek` (Task 7), `DailyMetrics` (Task 7).
- Produces:
  - `sleepFormat.ts`: `formatHm(minutes: number): string` → e.g. `"7h 42m"`; `toStageView(deep, rem, light, total)` → `{ deep, light, rem }` each `{ text, percent, color }` matching `SleepStages` `Stage` shape (colors deep `#4338CA`, light `#818CF8`, rem `#C084FC`); `weekHours(metrics: DailyMetrics[]): number[]` → array of hours rounded to 1dp.
  - `useSleepData(): { loading, hasData, totalSleep, score, stages, weekly, estimated, reload }`.

The pure helpers in `sleepFormat.ts` are unit-tested. `useSleepData` is a thin fetch wrapper exercised by the screen in Task 9 (manual run); no hook test needed.

- [ ] **Step 1: Write the failing test for pure helpers**

```ts
import { formatHm, toStageView, weekHours } from '../sleepFormat';

describe('sleepFormat', () => {
  it('formatHm formats minutes', () => {
    expect(formatHm(462)).toBe('7h 42m');
    expect(formatHm(60)).toBe('1h 0m');
    expect(formatHm(0)).toBe('0h 0m');
  });

  it('toStageView computes percents against total', () => {
    const v = toStageView(60, 120, 300, 480);
    expect(v.deep.percent).toBe(13); // round(60/480*100)
    expect(v.rem.percent).toBe(25);
    expect(v.light.percent).toBe(63);
    expect(v.deep.color).toBe('#4338CA');
  });

  it('toStageView handles zero total without NaN', () => {
    const v = toStageView(0, 0, 0, 0);
    expect(v.deep.percent).toBe(0);
  });

  it('weekHours converts minutes arrays to hours', () => {
    const out = weekHours([
      { sleepMinutes: 462 } as any,
      { sleepMinutes: null } as any,
    ]);
    expect(out).toEqual([7.7, 0]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=sleepFormat`
Expected: FAIL — `sleepFormat` not found.

- [ ] **Step 3: Implement `sleepFormat.ts`**

```ts
import type { DailyMetrics } from '../../types/home';

const COLORS = { deep: '#4338CA', light: '#818CF8', rem: '#C084FC' };

export const formatHm = (minutes: number): string => {
  const m = Math.max(0, Math.round(minutes));
  return `${Math.floor(m / 60)}h ${m % 60}m`;
};

const pct = (part: number, total: number): number =>
  total > 0 ? Math.round((part / total) * 100) : 0;

export const toStageView = (
  deep: number,
  rem: number,
  light: number,
  total: number,
) => ({
  deep: { text: formatHm(deep), percent: pct(deep, total), color: COLORS.deep },
  light: { text: formatHm(light), percent: pct(light, total), color: COLORS.light },
  rem: { text: formatHm(rem), percent: pct(rem, total), color: COLORS.rem },
});

export const weekHours = (metrics: DailyMetrics[]): number[] =>
  metrics.map(m =>
    m.sleepMinutes ? Math.round((m.sleepMinutes / 60) * 10) / 10 : 0,
  );
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=sleepFormat`
Expected: PASS (4 tests).

- [ ] **Step 5: Implement `useSleepData.ts`**

```ts
import { useCallback, useEffect, useState } from 'react';
import { fetchDayAggregate } from '../../services/calendarService';
import { fetchSleepWeek } from '../../services/sleepService';
import { formatHm, toStageView, weekHours } from './sleepFormat';

const todayStr = () => new Date().toISOString().slice(0, 10);
const daysAgoStr = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
};

export const useSleepData = () => {
  const [loading, setLoading] = useState(true);
  const [day, setDay] = useState<Awaited<
    ReturnType<typeof fetchDayAggregate>
  > | null>(null);
  const [weekly, setWeekly] = useState<number[]>([]);

  const reload = useCallback(async () => {
    setLoading(true);
    const today = todayStr();
    const [d, week] = await Promise.all([
      fetchDayAggregate(today),
      fetchSleepWeek(daysAgoStr(6), today),
    ]);
    setDay(d);
    setWeekly(weekHours(week));
    setLoading(false);
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const total = day?.sleepMinutes ?? 0;
  const hasData = total > 0;

  return {
    loading,
    hasData,
    totalSleep: formatHm(total),
    score: day?.sleepScore ?? 0,
    stages: toStageView(
      day?.deepMinutes ?? 0,
      day?.remMinutes ?? 0,
      day?.lightMinutes ?? 0,
      total,
    ),
    weekly,
    estimated: hasData, // manual logs are estimated; real-segment logs are rare in v1
    reload,
  };
};
```

- [ ] **Step 6: Commit**

```bash
cd DACN2_FEserver
git add src/screens/SleepTracking/useSleepData.ts src/screens/SleepTracking/sleepFormat.ts src/screens/SleepTracking/__tests__/sleepFormat.test.ts
git commit -m "feat(sleep): add useSleepData hook + pure formatting helpers"
```

---

## Task 9: Wire SleepTrackingScreen to real data + empty state + log button

**Files:**
- Modify: `DACN2_FEserver/src/screens/SleepTracking/SleepTrackingScreen.tsx`
- Modify: `DACN2_FEserver/src/context/SleepContext.tsx` (remove `MOCK_SLEEP_DATA`, `MOCK_WEEKLY`; keep `WEEK_LABELS`, `WEEK_MAX_HOURS`)

**Interfaces:**
- Consumes: `useSleepData` (Task 8); `LogSleepModal` (Task 10 — imported but the modal file is created in Task 10; to keep this task independently runnable, add a temporary inline placeholder that is replaced in Task 10. See Step 3).
- Produces: a working screen rendering real data with an empty state and a "Log sleep" button.

> Ordering note: Task 10 creates `LogSleepModal`. To let Task 9 compile and run on its own, Step 3 wires a minimal local modal stub; Task 10 replaces the import. If executing strictly in order with subagents, it is acceptable to do Task 10 first — both are listed; pick one ordering and keep the import consistent.

- [ ] **Step 1: Remove mock constants from `SleepContext.tsx`**

In `SleepContext.tsx`, delete the `MOCK_SLEEP_DATA` block (lines 14–27) and the `MOCK_WEEKLY` block (lines 29–32). Keep `WEEK_LABELS` and `WEEK_MAX_HOURS`.

- [ ] **Step 2: Verify nothing else imports the removed constants**

Run: `cd DACN2_FEserver && npx tsc --noEmit`
Expected: errors ONLY in `SleepTrackingScreen.tsx` (still importing the mocks). If any other file errors, note it — those imports must be migrated to `useSleepData` too.

- [ ] **Step 3: Rewrite `SleepTrackingScreen.tsx`**

```tsx
import React, { useState } from 'react';
import { View, StatusBar, ScrollView, Text, Pressable, ActivityIndicator } from 'react-native';
import { useNavigation } from '@react-navigation/native';

import styles from '@components/SleepTracking/styles';
import NightBackground from '@components/SleepTracking/NightBackground/NightBackground';
import SleepHeader from '@components/SleepTracking/SleepHeader/SleepHeader';
import SleepDial from '@components/SleepTracking/SleepDial/SleepDial';
import ScheduleRow from '@components/SleepTracking/ScheduleRow/ScheduleRow';
import SleepStages from '@components/SleepTracking/SleepStages/SleepStages';
import WeeklyTrend from '@components/SleepTracking/WeeklyTrend/WeeklyTrend';
import { useSleepData } from './useSleepData';
import LogSleepModal from './LogSleepModal';

const SleepTrackerScreen: React.FC = () => {
  const navigation = useNavigation();
  const { loading, hasData, totalSleep, score, stages, weekly, estimated, reload } =
    useSleepData();
  const [showLog, setShowLog] = useState(false);

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#312E81" />
      <NightBackground />
      <SleepHeader onBack={() => navigation.goBack()} />

      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentContainer}
        showsVerticalScrollIndicator={false}
      >
        {loading ? (
          <ActivityIndicator color="#A5F3E0" style={{ marginTop: 40 }} />
        ) : !hasData ? (
          <View style={{ alignItems: 'center', marginTop: 40 }}>
            <Text style={{ color: '#A5F3E0', fontSize: 16, marginBottom: 16 }}>
              Chưa có dữ liệu giấc ngủ hôm nay
            </Text>
            <Pressable
              onPress={() => setShowLog(true)}
              style={{ backgroundColor: '#6366F1', paddingVertical: 12, paddingHorizontal: 24, borderRadius: 12 }}
            >
              <Text style={{ color: '#fff', fontWeight: '600' }}>Ghi lại giấc ngủ</Text>
            </Pressable>
          </View>
        ) : (
          <>
            <SleepDial score={score} totalSleep={totalSleep} />
            {estimated ? (
              <Text style={{ color: '#A5F3E0', textAlign: 'center', fontSize: 12, marginTop: 4 }}>
                * Giai đoạn ngủ là ước lượng từ thời lượng
              </Text>
            ) : null}
            <View style={styles.analysisCard}>
              <SleepStages stages={stages} />
              <WeeklyTrend weeklyData={weekly} />
            </View>
            <Pressable
              onPress={() => setShowLog(true)}
              style={{ backgroundColor: '#6366F1', paddingVertical: 12, borderRadius: 12, alignItems: 'center', marginTop: 16 }}
            >
              <Text style={{ color: '#fff', fontWeight: '600' }}>Ghi lại giấc ngủ</Text>
            </Pressable>
          </>
        )}
      </ScrollView>

      <LogSleepModal
        visible={showLog}
        onClose={() => setShowLog(false)}
        onSaved={() => {
          setShowLog(false);
          reload();
        }}
      />
    </View>
  );
};

export default SleepTrackerScreen;
```

Note: this imports `ScheduleRow` but the empty/real layout above omits it for brevity; if bedtime/wake display is desired in v1, add `<ScheduleRow bedTime={...} wakeTime={...} />` fed from a `GET /health/sleep` day fetch. For v1, ScheduleRow is optional — remove the unused import if not rendering it to keep lint clean.

- [ ] **Step 4: Run typecheck + lint**

Run: `cd DACN2_FEserver && npx tsc --noEmit && npm run lint`
Expected: no errors (assumes Task 10's `LogSleepModal` exists; if doing Task 9 first, temporarily stub `LogSleepModal` as a component returning `null`).

- [ ] **Step 5: Commit**

```bash
cd DACN2_FEserver
git add src/screens/SleepTracking/SleepTrackingScreen.tsx src/context/SleepContext.tsx
git commit -m "feat(sleep): wire Sleep screen to real data with empty state + log entry"
```

---

## Task 10: LogSleepModal (timer + manual form)

**Files:**
- Create: `DACN2_FEserver/src/screens/SleepTracking/LogSleepModal.tsx`
- Test: `DACN2_FEserver/src/screens/SleepTracking/__tests__/logSleepValidation.test.ts`
- Create: `DACN2_FEserver/src/screens/SleepTracking/logSleepValidation.ts` (pure guard helpers)

**Interfaces:**
- Consumes: `createSleepSession` (Task 7).
- Produces:
  - `logSleepValidation.ts`: `buildLastNightRange(now: Date): { startAt: string; endAt: string }` (defaults 23:00 previous day → 07:00 today, ISO); `validateRange(startAt: string, endAt: string, now: Date): string | null` (returns an error message or `null`): rejects when `endAt <= startAt` or `endAt` is in the future.
  - `LogSleepModal: React.FC<{ visible: boolean; onClose: () => void; onSaved: () => void }>` with a Timer tab (Start/Stop) and a Manual tab (bedtime/wake pickers).

- [ ] **Step 1: Write the failing test for validation helpers**

```ts
import { buildLastNightRange, validateRange } from '../logSleepValidation';

describe('logSleepValidation', () => {
  const now = new Date('2026-06-26T09:00:00.000Z');

  it('buildLastNightRange returns prev-night 23:00 -> today 07:00', () => {
    const r = buildLastNightRange(now);
    expect(r.startAt).toContain('2026-06-25');
    expect(r.endAt).toContain('2026-06-26');
    expect(new Date(r.endAt).getTime()).toBeGreaterThan(
      new Date(r.startAt).getTime(),
    );
  });

  it('validateRange rejects end <= start', () => {
    expect(
      validateRange(
        '2026-06-26T07:00:00.000Z',
        '2026-06-26T07:00:00.000Z',
        now,
      ),
    ).not.toBeNull();
  });

  it('validateRange rejects future end', () => {
    expect(
      validateRange(
        '2026-06-26T08:00:00.000Z',
        '2026-06-26T23:00:00.000Z',
        now,
      ),
    ).not.toBeNull();
  });

  it('validateRange accepts a valid past range', () => {
    expect(
      validateRange(
        '2026-06-25T23:00:00.000Z',
        '2026-06-26T07:00:00.000Z',
        now,
      ),
    ).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=logSleepValidation`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `logSleepValidation.ts`**

```ts
export const buildLastNightRange = (
  now: Date,
): { startAt: string; endAt: string } => {
  const end = new Date(now);
  end.setHours(7, 0, 0, 0);
  const start = new Date(end);
  start.setDate(start.getDate() - 1);
  start.setHours(23, 0, 0, 0);
  return { startAt: start.toISOString(), endAt: end.toISOString() };
};

export const validateRange = (
  startAt: string,
  endAt: string,
  now: Date,
): string | null => {
  const s = new Date(startAt).getTime();
  const e = new Date(endAt).getTime();
  if (!(e > s)) return 'Giờ thức phải sau giờ ngủ.';
  if (e > now.getTime()) return 'Không thể ghi giấc ngủ trong tương lai.';
  return null;
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=logSleepValidation`
Expected: PASS (4 tests).

- [ ] **Step 5: Implement `LogSleepModal.tsx`**

```tsx
import React, { useEffect, useMemo, useState } from 'react';
import {
  Modal,
  View,
  Text,
  Pressable,
  Alert,
  Platform,
} from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { createSleepSession } from '../../services/sleepService';
import { buildLastNightRange, validateRange } from './logSleepValidation';

type Props = {
  visible: boolean;
  onClose: () => void;
  onSaved: () => void;
};

type Tab = 'timer' | 'manual';

const LogSleepModal: React.FC<Props> = ({ visible, onClose, onSaved }) => {
  const [tab, setTab] = useState<Tab>('manual');
  const [timerStart, setTimerStart] = useState<number | null>(null);
  const defaults = useMemo(() => buildLastNightRange(new Date()), []);
  const [bed, setBed] = useState<Date>(new Date(defaults.startAt));
  const [wake, setWake] = useState<Date>(new Date(defaults.endAt));
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!visible) {
      setSaving(false);
    }
  }, [visible]);

  const save = async (startAt: string, endAt: string, source: string) => {
    const err = validateRange(startAt, endAt, new Date());
    if (err) {
      Alert.alert('Không hợp lệ', err);
      return;
    }
    setSaving(true);
    const ok = await createSleepSession({ startAt, endAt, source });
    setSaving(false);
    if (ok) {
      onSaved();
    } else {
      Alert.alert('Lỗi', 'Không lưu được giấc ngủ. Vui lòng thử lại.');
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <View style={{ backgroundColor: '#1E1B4B', padding: 20, borderTopLeftRadius: 20, borderTopRightRadius: 20 }}>
          <View style={{ flexDirection: 'row', marginBottom: 16 }}>
            <TabButton label="Hẹn giờ" active={tab === 'timer'} onPress={() => setTab('timer')} />
            <TabButton label="Nhập tay" active={tab === 'manual'} onPress={() => setTab('manual')} />
          </View>

          {tab === 'timer' ? (
            <View>
              {timerStart == null ? (
                <PrimaryButton label="Bắt đầu ngủ" onPress={() => setTimerStart(Date.now())} />
              ) : (
                <PrimaryButton
                  label="Tôi đã thức dậy"
                  disabled={saving}
                  onPress={() =>
                    save(new Date(timerStart).toISOString(), new Date().toISOString(), 'timer')
                  }
                />
              )}
            </View>
          ) : (
            <View>
              <Text style={{ color: '#C7D2FE', marginBottom: 8 }}>Giờ ngủ</Text>
              <DateTimePicker
                value={bed}
                mode="datetime"
                display={Platform.OS === 'ios' ? 'spinner' : 'default'}
                onChange={(_, d) => d && setBed(d)}
              />
              <Text style={{ color: '#C7D2FE', marginTop: 12, marginBottom: 8 }}>Giờ thức</Text>
              <DateTimePicker
                value={wake}
                mode="datetime"
                display={Platform.OS === 'ios' ? 'spinner' : 'default'}
                onChange={(_, d) => d && setWake(d)}
              />
              <PrimaryButton
                label="Lưu"
                disabled={saving}
                onPress={() => save(bed.toISOString(), wake.toISOString(), 'manual')}
              />
            </View>
          )}

          <Pressable onPress={onClose} style={{ marginTop: 16, alignItems: 'center' }}>
            <Text style={{ color: '#A5B4FC' }}>Đóng</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
};

const TabButton: React.FC<{ label: string; active: boolean; onPress: () => void }> = ({
  label,
  active,
  onPress,
}) => (
  <Pressable
    onPress={onPress}
    style={{ flex: 1, paddingVertical: 10, borderBottomWidth: 2, borderBottomColor: active ? '#6366F1' : 'transparent' }}
  >
    <Text style={{ color: active ? '#fff' : '#A5B4FC', textAlign: 'center', fontWeight: '600' }}>
      {label}
    </Text>
  </Pressable>
);

const PrimaryButton: React.FC<{ label: string; onPress: () => void; disabled?: boolean }> = ({
  label,
  onPress,
  disabled,
}) => (
  <Pressable
    onPress={onPress}
    disabled={disabled}
    style={{ backgroundColor: disabled ? '#4B5563' : '#6366F1', paddingVertical: 14, borderRadius: 12, alignItems: 'center', marginTop: 16 }}
  >
    <Text style={{ color: '#fff', fontWeight: '600' }}>{label}</Text>
  </Pressable>
);

export default LogSleepModal;
```

> Dependency note: this uses `@react-native-community/datetimepicker`. Check it is already installed: `cd DACN2_FEserver && npm ls @react-native-community/datetimepicker`. If absent, install with `npm install @react-native-community/datetimepicker` and re-run the Android/iOS build (native module — requires a rebuild, not just Metro reload). If the project already has a date/time picker component, use that instead to avoid a new native dependency.

- [ ] **Step 6: Run typecheck + lint + tests**

Run: `cd DACN2_FEserver && npx tsc --noEmit && npm run lint && npm test -- --testPathPattern=logSleepValidation`
Expected: no errors; validation tests pass.

- [ ] **Step 7: Commit**

```bash
cd DACN2_FEserver
git add src/screens/SleepTracking/LogSleepModal.tsx src/screens/SleepTracking/logSleepValidation.ts src/screens/SleepTracking/__tests__/logSleepValidation.test.ts
git commit -m "feat(sleep): add LogSleepModal with timer + manual entry and guards"
```

---

## Task 11: Unify 8h goal in Home card + Calendar status

**Files:**
- Modify: `DACN2_FEserver/src/components/Home/HeartSleepGrid/HeartSleepGrid.tsx:19-25,59-88`
- Modify: `DACN2_FEserver/src/components/Calendar/DailySummary/DailySummary.tsx:33-38`
- Modify: `DACN2_FEserver/src/utils/healthStatus.ts:20,29-32`

**Interfaces:**
- Consumes: nothing new.
- Produces: all three surfaces use the unified 8h (480 min) target and the same status thresholds: `< 360` Low, `360–540` Normal, `> 540` Long.

This is a consistency cleanup verified by typecheck + a focused status test.

- [ ] **Step 1: Write the failing test for healthStatus sleep range**

```ts
import { isOutOfRange } from '../healthStatus';

describe('healthStatus sleep range (unified 8h)', () => {
  it('flags under 6h as out of range', () => {
    expect(isOutOfRange('sleep', 300)).toBe(true); // 5h
  });
  it('treats 6-9h as in range', () => {
    expect(isOutOfRange('sleep', 480)).toBe(false); // 8h
  });
  it('flags over 9h as out of range', () => {
    expect(isOutOfRange('sleep', 600)).toBe(true); // 10h
  });
});
```

(Adjust the `isOutOfRange` call signature to match the actual one in `healthStatus.ts` — read lines 29–32 first and mirror its parameter shape.)

- [ ] **Step 2: Run test to verify current behavior**

Run: `cd DACN2_FEserver && npm test -- --testPathPattern=healthStatus`
Expected: depends on current thresholds. Read `healthStatus.ts:29-32`; if the sleep range already matches 360/540, the test passes and Step 3's healthStatus edit is a no-op — only the Home card target text (8h) needs changing.

- [ ] **Step 3: Unify thresholds**

3a. In `HeartSleepGrid.tsx`, ensure the status function (lines 19–25) uses `< 360` Low, `360–540` Normal, `> 540` Long, and the displayed target (lines 59–88) reads **8h** (480 min), not the current mixed value.

3b. In `DailySummary.tsx` (lines 33–38), confirm Short `< 360` / Normal `360–540` / Long `> 540` — already matches; no change unless the target label says otherwise.

3c. In `healthStatus.ts`, ensure the sleep branch (lines 29–32) uses `min < 360 || min > 540`.

- [ ] **Step 4: Run typecheck + lint + tests**

Run: `cd DACN2_FEserver && npx tsc --noEmit && npm run lint && npm test -- --testPathPattern=healthStatus`
Expected: no errors; status tests pass.

- [ ] **Step 5: Commit**

```bash
cd DACN2_FEserver
git add src/components/Home/HeartSleepGrid/HeartSleepGrid.tsx src/components/Calendar/DailySummary/DailySummary.tsx src/utils/healthStatus.ts src/utils/__tests__/healthStatus.test.ts
git commit -m "fix(sleep): unify 8h goal + status thresholds across home and calendar"
```

---

## Task 12: End-to-end manual verification

**Files:** none (verification only).

No automated test; this confirms the full loop works against a running stack.

- [ ] **Step 1: Start the backend**

Run: `cd DACN2_BEserver && ./mvnw spring-boot:run`
Expected: starts on port 8080.

- [ ] **Step 2: Start the app**

Run: `cd DACN2_FEserver && npm start` then `npm run android` (or `npm run ios`).
Expected: app builds; if `@react-native-community/datetimepicker` was newly installed, a native rebuild is required.

- [ ] **Step 3: Verify empty state**

Open Browser tab → Sleep Tracking. With no sleep logged today, expect the "Chưa có dữ liệu giấc ngủ hôm nay" empty state + "Ghi lại giấc ngủ" button.

- [ ] **Step 4: Verify manual logging**

Tap "Ghi lại giấc ngủ" → Manual tab → defaults to last night (23:00 → 07:00) → Save. Expect the screen to reload showing the dial score, estimated-stages note, sleep stages, and the weekly trend with today's bar populated.

- [ ] **Step 5: Verify guards**

Open the modal again, set wake-time to a future time → Save → expect the "Không thể ghi giấc ngủ trong tương lai." alert and no save.

- [ ] **Step 6: Verify replace-on-same-day**

Log sleep again for today with a different duration → expect the day's total to be REPLACED (not summed) on reload, and the Home card + Calendar to show the same value.

- [ ] **Step 7: Final commit (if any verification fixes were needed)**

Commit any small fixes discovered during verification with an appropriate `fix(sleep): ...` message in the correct repo.

---

## Self-Review

**Spec coverage:**
- Real data on Sleep screen → Tasks 7–9. ✓
- Live timer + manual form → Task 10. ✓
- Backend estimates stages from duration, flagged estimated → Tasks 1, 5 (estimate), 8/9 (flag). ✓
- Backend 0–100 score → Tasks 2, 4, 6. ✓
- Fixed unified 8h goal → Tasks 4 (BE constant + highlights), 11 (FE). ✓
- Real 7-day trend → Tasks 7 (`fetchSleepWeek`), 8 (`weekHours`), 9 (render). ✓
- Safety guards: future prevention → Tasks 5 (BE), 10 (FE); replace-on-same-day → Tasks 4–5; default last night → Task 10. ✓
- Error handling table (future/invalid range/replace warn/network/empty state/fetch fail) → Tasks 5, 9, 10. ✓ (Network failure surfaces an Alert in Task 10; timer-state persistence across app-close is simplified to in-memory in v1 — noted as a deliberate v1 simplification below.)

**Deviations from spec (deliberate, v1):**
- Timer `startAt` persistence across full app-close is in-memory only in this plan (not AsyncStorage). The spec called for persistence; this is deferred to keep Task 10 focused. If required for v1, add an AsyncStorage read/write of `timerStart` in `LogSleepModal` — flag to the user before implementation.
- `ScheduleRow` (bedtime/wake display) is optional in v1 since the aggregate doesn't carry the session time range; populating it needs a `GET /health/sleep` day fetch. Noted in Task 9.

**Placeholder scan:** No TBD/TODO; every code step has full code. ✓

**Type consistency:** `addSleep` 8-arg signature consistent across Tasks 4–5; `SleepStageEstimator.Stages`/`SleepScorer.Result` records consistent across Tasks 1–2, 4–5; `DailyMetrics` fields consistent across Tasks 7–8; `createSleepSession` signature consistent Tasks 7, 10; `LogSleepModal` props consistent Tasks 9–10. ✓
