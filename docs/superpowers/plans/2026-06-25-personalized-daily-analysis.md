# Personalized Daily Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/ai/daily-analysis` compute a personalized readiness score and action plan against each user's own goals (`UserGoals`), using health data the server reads from `DailyAggregate` by JWT principal.

**Architecture:** A pure, side-effect-free `ReadinessScorer` holds the scoring math and action-plan text. `AiAnalysisService` orchestrates: reads `User` (goals + timezone) and `DailyAggregate` from repositories, resolves goal defaults, calls the scorer, and builds the response. Backward compatibility: if no `DailyAggregate` exists for the date but the request still carries a `summary` map (old FE), the service falls back to parsing it. Frontend stops sending `summary`, reads goals from `UserContext` to feed the home `ActivityCard` real targets.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data MongoDB, JUnit 5 + Mockito (via `spring-boot-starter-test`), Maven wrapper. Frontend: React Native 0.82 / TypeScript, Axios.

## Global Constraints

- Backend routes have **no `/api` prefix**. The endpoint stays `POST /ai/daily-analysis`.
- API responses are wrapped in `{ data: ... }` via `ApiResponse.ok(...)`; FE strips with `unwrapApiData()` / reads `res.data?.data`.
- `readinessScore` final value clamps to `[50, 95]`. Empty-data case returns exactly `78`.
- Scoring weights (base + criteria = 100): base `60`, steps max `15`, water max `10`, caloriesOut max `5`, sleep max `10`.
- Goal defaults when a user goal is null or ≤ 0: steps `8000`, water `2000` ml, caloriesOut `500`. Sleep ideal range `7–9` hours (acceptable `6–10`).
- `DEFAULT_CALORIES_OUT_GOAL` already equals `500` in `DailyAggregateService` — keep consistent.
- Existing user-facing copy (`targetUsers`, `missingForHealthApp`, `optimizations`, `disclaimer`) is **unchanged**.
- Tests must not require a live MongoDB/Redis: use plain unit tests for the scorer and Mockito for the service. Do **not** use `@SpringBootTest`.
- `User.getGoals()` is never null (`@Builder.Default`), but its fields may be null. `User.getSettings()` may be null; timezone falls back to `UTC` (mirror `CalendarService.safeZoneId`).

---

## File Structure

**Backend (`DACN2_BEserver`):**
- Create `src/main/java/com/example/dacn2_beserver/service/ai/ReadinessScorer.java` — pure scoring + action-plan + summary text. No Spring, no I/O.
- Modify `src/main/java/com/example/dacn2_beserver/service/ai/AiAnalysisService.java` — add `analyzeDaily(String userId, DailyAnalysisRequest)` overload that reads repos, resolves goals, delegates to scorer.
- Modify `src/main/java/com/example/dacn2_beserver/controller/AiController.java` — pass `principal.userId()` into the service.
- Create `src/test/java/com/example/dacn2_beserver/service/ai/ReadinessScorerTest.java` — pure unit tests.
- Create `src/test/java/com/example/dacn2_beserver/service/ai/AiAnalysisServiceTest.java` — Mockito tests.

> No DTO change needed: `DailyAnalysisRequest` keeps `date` + optional `summary`; `DailyAnalysisResponse` unchanged. `UserResponse` already exposes `goals`.

**Frontend (`DACN2_FEserver`):**
- Modify `src/context/UserContext.tsx` — add `goals` to the `User` interface.
- Modify `src/screens/AppScreen/AIAnalysis/AiAnalysisScreen.tsx` — POST only `{ date }`.
- Modify `src/screens/AppScreen/Home/HomeScreen.tsx` — pass real `targetSteps`/`targetCalories` from `user.goals`.

---

## Task 1: ReadinessScorer (pure scoring unit)

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/ai/ReadinessScorer.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/ai/ReadinessScorerTest.java`

**Interfaces:**
- Consumes: nothing (leaf unit).
- Produces (later tasks rely on these exact shapes):
  - `ReadinessScorer.Goals` — `record Goals(int steps, int waterMl, int caloriesOut)` (already-resolved, all > 0).
  - `ReadinessScorer.DailyMetrics` — `record DailyMetrics(Double steps, Double waterMl, Double caloriesOut, Double sleepHours)` (any field may be null; all null = empty).
  - `ReadinessScorer.ScoreResult` — `record ScoreResult(int score, java.util.List<String> actionPlan, String summary)`.
  - `ScoreResult score(DailyMetrics m, Goals g, String dateLabel)` — instance method (the class is a Spring `@Component` so it can be injected, but the method is pure).

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/ai/ReadinessScorerTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=ReadinessScorerTest`
Expected: FAIL — compilation error, `ReadinessScorer` class does not exist.

- [ ] **Step 3: Write the implementation**

Create `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/ai/ReadinessScorer.java`:

```java
package com.example.dacn2_beserver.service.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chấm điểm sẵn sàng (readiness) thuần: so chỉ số trong ngày với mục tiêu cá nhân.
 * Không I/O, không phụ thuộc trạng thái — dễ test độc lập.
 */
@Component
public class ReadinessScorer {

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
            parts.add(String.format("%,d bước", m.steps().intValue()));
        }
        if (m.waterMl() != null) {
            parts.add(String.format("%,dml nước", m.waterMl().intValue()));
        }
        if (m.sleepHours() != null) {
            parts.add(String.format("%.1f giờ ngủ", m.sleepHours()));
        }
        if (m.caloriesOut() != null) {
            parts.add(String.format("%,d kcal tiêu hao", m.caloriesOut().intValue()));
        }
        return "Phân tích ngày " + date + ": chỉ số sẵn sàng " + score + "/100 dựa trên "
                + String.join(", ", parts) + " so với mục tiêu cá nhân của bạn.";
    }

    private List<String> buildActionPlan(DailyMetrics m, Goals g) {
        List<String> plan = new ArrayList<>();

        if (m.steps() != null && m.steps() < g.steps()) {
            int remaining = (int) Math.round(g.steps() - m.steps());
            plan.add(String.format("Còn %,d bước nữa để đạt mục tiêu %,d bước của bạn — "
                    + "thử đi bộ 10-15 phút sau bữa ăn.", remaining, g.steps()));
        } else if (m.steps() != null) {
            plan.add(String.format("Bạn đã đạt mục tiêu %,d bước hôm nay 👏 Duy trì vận động đều đặn.",
                    g.steps()));
        } else {
            plan.add("Duy trì vận động đều và xen kẽ nghỉ ngơi hợp lý.");
        }

        if (m.waterMl() != null && m.waterMl() < g.waterMl()) {
            int remaining = (int) Math.round(g.waterMl() - m.waterMl());
            plan.add(String.format("Còn khoảng %,dml nữa để đạt mục tiêu %,dml — "
                    + "uống rải đều theo từng khung giờ.", remaining, g.waterMl()));
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=ReadinessScorerTest`
Expected: PASS — all 11 tests green.

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/ai/ReadinessScorer.java \
        src/test/java/com/example/dacn2_beserver/service/ai/ReadinessScorerTest.java
git commit -m "feat(ai): add pure ReadinessScorer for goal-based readiness scoring"
```

---

## Task 2: AiAnalysisService personalization

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/ai/AiAnalysisService.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/ai/AiAnalysisServiceTest.java`

**Interfaces:**
- Consumes: `ReadinessScorer` (Task 1) — `Goals`, `DailyMetrics`, `ScoreResult`, `score(...)`.
- Consumes (existing): `UserRepository.findById(String) : Optional<User>`; `DailyAggregateRepository.findByUserIdAndDate(String, LocalDate) : Optional<DailyAggregate>`; `User.getGoals() : UserGoals`; `User.getSettings()`; `DailyAggregate` getters `getSteps()/getWaterMl()/getCaloriesOut()/getSleepMinutes()` (all `Integer`); `ApiException(ErrorCode.USER_NOT_FOUND, msg)`.
- Produces: `DailyAnalysisResponse analyzeDaily(String userId, DailyAnalysisRequest req)` — new overload used by the controller in Task 3.

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/ai/AiAnalysisServiceTest.java`:

```java
package com.example.dacn2_beserver.service.ai;

import com.example.dacn2_beserver.dto.ai.DailyAnalysisRequest;
import com.example.dacn2_beserver.dto.ai.DailyAnalysisResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.repository.AiFeedbackRepository;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock UserRepository userRepository;
    @Mock DailyAggregateRepository dailyAggregateRepository;
    @Mock AiFeedbackRepository aiFeedbackRepository;

    AiAnalysisService service;

    private User userWithGoals(Integer stepGoal) {
        return User.builder()
                .id("u1")
                .goals(UserGoals.builder().dailySteps(stepGoal).dailyWaterMl(2000).dailyCaloriesOut(500).build())
                .build();
    }

    @BeforeEach
    void wireScorer() {
        // ReadinessScorer is a real collaborator, not a mock; @InjectMocks won't supply it.
        service = new AiAnalysisService(aiFeedbackRepository, userRepository,
                dailyAggregateRepository, new ReadinessScorer());
    }

    @Test
    void usesDailyAggregateWhenPresent() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(LocalDate.parse("2026-06-25"))
                .steps(8000).waterMl(2000).caloriesOut(500).sleepMinutes(480)
                .build();
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.of(agg));

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        assertThat(res.getReadinessScore()).isEqualTo(95);
        assertThat(res.getActionPlan()).anyMatch(s -> s.contains("đạt mục tiêu"));
    }

    @Test
    void fallsBackToSummaryWhenNoAggregate() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.empty());

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", Map.of("steps", 8000));
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        // steps met (60 + 15 = 75), water/calo/sleep absent
        assertThat(res.getReadinessScore()).isEqualTo(75);
    }

    @Test
    void emptyDataReturns78() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.empty());

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        assertThat(res.getReadinessScore()).isEqualTo(78);
    }

    @Test
    void nullGoalUsesDefault() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(null)));
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(LocalDate.parse("2026-06-25")).steps(8000).build();
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.of(agg));

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        // default step goal 8000, met => 60 + 15 = 75
        assertThat(res.getReadinessScore()).isEqualTo(75);
    }

    @Test
    void unknownUserThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        assertThatThrownBy(() -> service.analyzeDaily("ghost", req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void keepsStaticDescriptiveSections() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.empty());
        DailyAnalysisResponse res = service.analyzeDaily("u1", new DailyAnalysisRequest("2026-06-25", null));
        assertThat(res.getTargetUsers()).isNotEmpty();
        assertThat(res.getDisclaimer()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=AiAnalysisServiceTest`
Expected: FAIL — `AiAnalysisService` has no 4-arg constructor and no `analyzeDaily(String, DailyAnalysisRequest)`.

- [ ] **Step 3: Rewrite AiAnalysisService**

Replace the full contents of `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/ai/AiAnalysisService.java` with:

```java
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
```

> Note on the summary fallback: `acc[2]` collects `sleep` and `acc[3]` collects `calories`, but `DailyMetrics` order is `(steps, waterMl, caloriesOut, sleepHours)`. The constructor call deliberately maps `acc[3]` → caloriesOut and `acc[2]` → sleepHours. Summary sleep values were historically passed in hours, so they flow straight through.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd DACN2_BEserver && ./mvnw test -Dtest=AiAnalysisServiceTest`
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/ai/AiAnalysisService.java \
        src/test/java/com/example/dacn2_beserver/service/ai/AiAnalysisServiceTest.java
git commit -m "feat(ai): personalize daily analysis against user goals and DailyAggregate"
```

---

## Task 3: Wire controller to JWT principal

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/controller/AiController.java`

**Interfaces:**
- Consumes: `AiAnalysisService.analyzeDaily(String userId, DailyAnalysisRequest)` (Task 2); `AuthPrincipal.userId()` (existing, already used by `/feedback` in the same controller).
- Produces: unchanged HTTP contract `POST /ai/daily-analysis` → `ApiResponse<DailyAnalysisResponse>`.

- [ ] **Step 1: Edit the endpoint**

In `AiController.java`, replace the `dailyAnalysis` method (currently lines ~33-36):

```java
    @PostMapping(value = "/daily-analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DailyAnalysisResponse> dailyAnalysis(@RequestBody DailyAnalysisRequest req) {
        return ApiResponse.ok(aiAnalysisService.analyzeDaily(req));
    }
```

with:

```java
    @PostMapping(value = "/daily-analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DailyAnalysisResponse> dailyAnalysis(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody DailyAnalysisRequest req) {
        return ApiResponse.ok(aiAnalysisService.analyzeDaily(principal.userId(), req));
    }
```

The imports `org.springframework.security.core.annotation.AuthenticationPrincipal` and `com.example.dacn2_beserver.security.AuthPrincipal` already exist in this file (used by `feedback`). No new imports needed.

- [ ] **Step 2: Compile and run the full backend test suite**

Run: `cd DACN2_BEserver && ./mvnw test`
Expected: PASS — `ReadinessScorerTest` (11) + `AiAnalysisServiceTest` (6) green; build succeeds. (The old single-arg `analyzeDaily(DailyAnalysisRequest)` no longer exists; confirm nothing else references it — compile failure here would reveal it. Only `AiController` called it.)

- [ ] **Step 3: Commit**

```bash
cd DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/controller/AiController.java
git commit -m "feat(ai): resolve daily-analysis user from JWT principal"
```

---

## Task 4: Frontend — stop sending summary; surface real goals

**Files:**
- Modify: `DACN2_FEserver/src/context/UserContext.tsx`
- Modify: `DACN2_FEserver/src/screens/AppScreen/AIAnalysis/AiAnalysisScreen.tsx`
- Modify: `DACN2_FEserver/src/screens/AppScreen/Home/HomeScreen.tsx`

**Interfaces:**
- Consumes: backend `POST /ai/daily-analysis` now reads goals/metrics server-side (Tasks 2-3); `/auth/me` already returns `goals` in `UserResponse`.
- Produces: `User.goals` typed field consumed by `HomeScreen`.

- [ ] **Step 1: Add `goals` to the User interface**

In `UserContext.tsx`, inside the `export interface User { ... }` block, add after the `healthMetrics` field (before the closing brace at line ~33):

```ts
  goals?: {
    dailySteps?: number;
    dailyCaloriesIn?: number;
    dailyCaloriesOut?: number;
    dailyWaterMl?: number;
    targetWeightKg?: number;
  } | null;
```

- [ ] **Step 2: AI screen sends only date**

In `AiAnalysisScreen.tsx`, the `fetchAnalysis` body currently posts `{ date: selectedDate, summary }`. Replace that `api.post` call:

```ts
        const res = await api.post('/ai/daily-analysis', {
          date: selectedDate,
        });
```

Leave `summary` in `route.params` destructuring (other code paths may still pass it); it is simply no longer sent. The `fallbackAnalysis` block is unchanged.

> If ESLint flags `summary` as unused after this change, remove `summary` from the `const { selectedDate, summary } = route.params;` destructuring and from the `useEffect` dependency array (change `[selectedDate, summary]` to `[selectedDate]`).

- [ ] **Step 3: HomeScreen passes real targets**

In `HomeScreen.tsx`, find the `<ActivityCard ... />` usage. Ensure `useUser()` (or the existing user accessor in this file) is in scope, then pass real targets. The `ActivityCard` already defaults to 10000/1000 when these are undefined:

```tsx
        <ActivityCard
          steps={/* existing steps expression */}
          calories={/* existing calories expression */}
          targetSteps={user?.goals?.dailySteps ?? undefined}
          targetCalories={user?.goals?.dailyCaloriesOut ?? undefined}
        />
```

If `HomeScreen.tsx` does not already import the user, add at the top with the other imports:

```tsx
import { useUser } from '@context/UserContext';
```

and inside the component body:

```tsx
  const { user } = useUser();
```

(Keep the existing `steps`/`calories` expressions exactly as they were — only the two `target*` props are added.)

- [ ] **Step 4: Lint and type-check**

Run: `cd DACN2_FEserver && npm run lint`
Expected: PASS — no new ESLint errors in the three modified files.

- [ ] **Step 5: Commit**

```bash
cd DACN2_FEserver
git add src/context/UserContext.tsx \
        src/screens/AppScreen/AIAnalysis/AiAnalysisScreen.tsx \
        src/screens/AppScreen/Home/HomeScreen.tsx
git commit -m "feat(ai): use server-side daily analysis and real goal targets on home"
```

---

## Manual verification (after all tasks)

1. Start backend: `cd DACN2_BEserver && ./mvnw spring-boot:run`.
2. With a user that has `dailySteps` set (e.g. 9000) and a `DailyAggregate` for today, call `POST /ai/daily-analysis` with body `{ "date": "<today>" }` and a valid Bearer token. Confirm `readinessScore` reflects steps-vs-9000 and `actionPlan` mentions the personal goal number.
3. Call with `{ "date": "<a day with no aggregate>" }` → expect score `78` and generic action plan, no error.
4. In the app, set a custom step goal in settings, open Home, confirm the FOOTSTEP progress bar target shows the custom value (not 10,000).
5. Open the AI Analysis screen for a date → confirm it loads a personalized score without sending `summary`.
