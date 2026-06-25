# Heart-Rate Persistence Implementation Plan (Spec 1 of 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist a heart-rate reading via `POST /health/heart-rate` (stored as a `HealthEventRaw`), recompute the day's avg/max/min into `DailyAggregate`, expose those fields through summary/calendar responses, and on the frontend save each measurement + show a heart-rate history screen.

**Architecture:** Follow the existing Water/Sleep pattern: a `HeartRateService` saves a raw event and calls `DailyAggregateService.addHeartRate`, which re-reads all of the user's HEART_RATE readings for that day and recomputes avg/max/min (exact, idempotent — not a running average). `SummaryService`/`CalendarService` gain the heart-rate fields they currently omit. The RN Edit/Result screen POSTs the BPM it already has; a new history screen lists past readings.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data MongoDB, JUnit 5 + Mockito (`spring-boot-starter-test`). Frontend: React Native 0.82 / TypeScript, Axios.

## Global Constraints

- Routes have no `/api` prefix; `/health/**` is JWT-gated — do NOT touch SecurityConfig. New routes: `POST /health/heart-rate`, `GET /health/heart-rate?from&to`.
- Heart-rate readings are stored as `HealthEventRaw` (collection `events_raw`) with `type = EventType.HEART_RATE`, `payload = { "bpm": <int> }`, `time = TimeRange(startAt=measuredAt, endAt=measuredAt)`, `createdAt = now`. NO new model.
- `bpm` valid range is `30..230` inclusive (`@NotNull @Min(30) @Max(230)`); `measuredAt` null → `Instant.now()`.
- `addHeartRate` RECOMPUTES avg/max/min from all of the user's HEART_RATE readings for that calendar day (in the user's timezone), keyed on `time.startAt`. avg = `Math.round(mean)`. It must be idempotent.
- The day for a reading = `LocalDateTime.ofInstant(measuredAt, userZone).toLocalDate()`, matching how `addWater` derives the date.
- `DailyAggregateResponse` ALREADY has `avgHeartRate/maxHeartRate/minHeartRate` fields (just unmapped). `CalendarDaySummaryResponse` does NOT — add them.
- API responses wrap in `{ data: ... }` via `ApiResponse.ok(...)`; FE strips with `unwrapApiData()`.
- Tests must NOT use `@SpringBootTest` (no live Mongo/Redis). Mockito for services.
- Running BE tests on this machine needs the Avast-truststore + cached Maven 3.9.11 workaround (below).

### Backend test command (this machine only)

```
cd /d/DATN/DACN2_BEserver
export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.17.10-hotspot"
export MAVEN_OPTS="-Djavax.net.ssl.trustStore=C:\\Users\\ASUS\\AppData\\Local\\Temp\\cacerts-avast -Djavax.net.ssl.trustStorePassword=changeit"
MVN="/c/Users/ASUS/.m2/wrapper/dists/apache-maven-3.9.11/03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc/bin/mvn"
"$MVN" test -Dtest=SomeTestClass
```

Use only focused `-Dtest=...`; never the full suite.

---

## File Structure

**Backend (`DACN2_BEserver`):**
- Modify `repository/HealthEventRawRepository.java` — add a query by `type` + `time.startAt` range.
- Create `dto/health/CreateHeartRateRequest.java`, `dto/health/HeartRateReadingResponse.java`.
- Modify `service/health/DailyAggregateService.java` — add `addHeartRate`.
- Create `service/health/HeartRateService.java`.
- Modify `controller/HealthController.java` — 2 routes.
- Modify `service/health/SummaryService.java` — map heart fields.
- Modify `dto/health/CalendarDaySummaryResponse.java` + `service/health/CalendarService.java` — add + map heart fields.
- Tests: `DailyAggregateServiceTest`, `HeartRateServiceTest`, `SummaryServiceTest`.

**Frontend (`DACN2_FEserver`):**
- Create `src/screens/HeartMeasurement/api.ts` — `saveHeartRate`, `fetchHeartRateHistory`.
- Modify `src/screens/HeartMeasurement/HeartResultScreen.tsx` — POST on mount.
- Create `src/screens/HeartMeasurement/HeartHistoryScreen.tsx`.
- Modify `src/navigation/AppStack/BrowserStack.tsx` — register `HeartHistory`.

---

## Task 1: Repository query for daily readings

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/repository/HealthEventRawRepository.java`

**Interfaces:**
- Produces: `List<HealthEventRaw> findAllByUserIdAndTypeAndTimeStartAtBetween(String userId, EventType type, Instant from, Instant to)` and `...OrderByTimeStartAtDesc(...)` — consumed by Tasks 2 (service) and the recompute in `DailyAggregateService`.

Pure interface addition; verified by compilation (Spring Data derives the query at startup, but we only compile here — the derived-query validity is exercised by the service tests via mocks, and at runtime by manual verification).

- [ ] **Step 1: Add the derived-query methods**

In `HealthEventRawRepository.java`, add two methods inside the interface:

```java
    List<HealthEventRaw> findAllByUserIdAndTypeAndTimeStartAtBetween(
            String userId, EventType type, Instant from, Instant to
    );

    List<HealthEventRaw> findAllByUserIdAndTypeAndTimeStartAtBetweenOrderByTimeStartAtDesc(
            String userId, EventType type, Instant from, Instant to
    );
```

(The model `HealthEventRaw` has `private TimeRange time;` and `TimeRange` has `startAt` — so `TimeStartAt` is the nested property path. `Instant` and `EventType` are already imported.)

- [ ] **Step 2: Compile to verify**

Run: `"$MVN" -o test-compile 2>&1 | tail -15`
Expected: BUILD SUCCESS. (If `-o` fails on missing artifacts, drop `-o`.)

- [ ] **Step 3: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/repository/HealthEventRawRepository.java
git commit -m "feat(health): add HealthEventRaw queries by type and time.startAt range"
```

---

## Task 2: DTOs for heart-rate request/response

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/CreateHeartRateRequest.java`
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/HeartRateReadingResponse.java`

**Interfaces:**
- Produces: `CreateHeartRateRequest` with `getBpm():Integer`, `getMeasuredAt():Instant`; `HeartRateReadingResponse` builder fields `id(String)`, `bpm(int)`, `measuredAt(Instant)`. Consumed by Tasks 3 (service) and 4 (controller).

Pure data classes; verified by compilation, exercised by Task 3 tests.

- [ ] **Step 1: Create CreateHeartRateRequest**

`DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/CreateHeartRateRequest.java`:

```java
package com.example.dacn2_beserver.dto.health;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHeartRateRequest {

    @NotNull
    @Min(30)
    @Max(230)
    private Integer bpm;

    /** Thời điểm đo; null -> now ở service. */
    private Instant measuredAt;
}
```

- [ ] **Step 2: Create HeartRateReadingResponse**

`DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/HeartRateReadingResponse.java`:

```java
package com.example.dacn2_beserver.dto.health;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartRateReadingResponse {
    private String id;
    private int bpm;
    private Instant measuredAt;
}
```

- [ ] **Step 3: Compile to verify**

Run: `"$MVN" -o test-compile 2>&1 | tail -15`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/dto/health/CreateHeartRateRequest.java \
        src/main/java/com/example/dacn2_beserver/dto/health/HeartRateReadingResponse.java
git commit -m "feat(health): add heart-rate request/response DTOs"
```

---

## Task 3: addHeartRate recompute in DailyAggregateService

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/DailyAggregateService.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/DailyAggregateServiceTest.java`

**Interfaces:**
- Consumes: `HealthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween` (Task 1); existing private helpers `requireUser`, `userZone`, `findOrCreate`, `touch`; `DailyAggregate` setters `setAvgHeartRate/setMaxHeartRate/setMinHeartRate`.
- Produces: `DailyAggregate addHeartRate(String userId, Instant measuredAt, int bpm)` — consumed by Task 4 service.

**Note on wiring:** `DailyAggregateService` currently has constructor deps `DailyAggregateRepository` + `UserRepository` (via `@RequiredArgsConstructor`). This task adds a `HealthEventRawRepository` dependency (a new `private final` field) so it can re-read readings.

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/DailyAggregateServiceTest.java`:

```java
package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserSettings;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyAggregateServiceTest {

    @Mock DailyAggregateRepository dailyAggregateRepository;
    @Mock UserRepository userRepository;
    @Mock HealthEventRawRepository healthEventRawRepository;

    @InjectMocks DailyAggregateService service;

    private User utcUser() {
        return User.builder().id("u1").username("dat")
                .settings(UserSettings.builder().timezone("UTC").build())
                .build();
    }

    private HealthEventRaw reading(int bpm, Instant at) {
        return HealthEventRaw.builder()
                .userId("u1").type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(at).endAt(at).build())
                .payload(Map.of("bpm", bpm))
                .createdAt(at)
                .build();
    }

    @Test
    void singleReadingSetsAllThreeEqual() {
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(utcUser()));
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(reading(72, at)));
        when(dailyAggregateRepository.save(any(DailyAggregate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyAggregate agg = service.addHeartRate("u1", at, 72);

        assertThat(agg.getAvgHeartRate()).isEqualTo(72);
        assertThat(agg.getMaxHeartRate()).isEqualTo(72);
        assertThat(agg.getMinHeartRate()).isEqualTo(72);
    }

    @Test
    void multipleReadingsComputeAvgMaxMin() {
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(utcUser()));
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(reading(60, at), reading(80, at), reading(100, at)));
        when(dailyAggregateRepository.save(any(DailyAggregate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyAggregate agg = service.addHeartRate("u1", at, 100);

        assertThat(agg.getAvgHeartRate()).isEqualTo(80);
        assertThat(agg.getMaxHeartRate()).isEqualTo(100);
        assertThat(agg.getMinHeartRate()).isEqualTo(60);
    }

    @Test
    void recomputeIsIdempotentForSameReadings() {
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(utcUser()));
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetween(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(reading(70, at), reading(90, at)));
        when(dailyAggregateRepository.save(any(DailyAggregate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyAggregate first = service.addHeartRate("u1", at, 90);
        DailyAggregate second = service.addHeartRate("u1", at, 90);

        assertThat(first.getAvgHeartRate()).isEqualTo(80);
        assertThat(second.getAvgHeartRate()).isEqualTo(80);
        assertThat(second.getMaxHeartRate()).isEqualTo(90);
        assertThat(second.getMinHeartRate()).isEqualTo(70);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$MVN" test -Dtest=DailyAggregateServiceTest`
Expected: FAIL — `addHeartRate` does not exist, and the constructor has no `HealthEventRawRepository` (compile error).

- [ ] **Step 3: Add the dependency + method**

In `DailyAggregateService.java`:

1. Add the repository field alongside the existing `private final` deps (with `@RequiredArgsConstructor`, declaring the field adds it to the constructor):

```java
    private final HealthEventRawRepository healthEventRawRepository;
```

Add the import:
```java
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import java.time.LocalTime;
```

2. Add the method (place it next to `addWater`):

```java
    public DailyAggregate addHeartRate(String userId, Instant measuredAt, int bpm) {
        User user = requireUser(userId);
        ZoneId zoneId = userZone(user);

        LocalDate date = LocalDateTime.ofInstant(measuredAt, zoneId).toLocalDate();

        // Day window [startOfDay, startOfNextDay) in the user's timezone, as instants.
        Instant from = date.atStartOfDay(zoneId).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<HealthEventRaw> readings = healthEventRawRepository
                .findAllByUserIdAndTypeAndTimeStartAtBetween(userId, EventType.HEART_RATE, from, to);

        List<Integer> bpms = new ArrayList<>();
        for (HealthEventRaw r : readings) {
            Integer v = extractBpm(r);
            if (v != null) {
                bpms.add(v);
            }
        }

        DailyAggregate agg = findOrCreate(userId, date);
        if (!bpms.isEmpty()) {
            int sum = 0, max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
            for (int v : bpms) {
                sum += v;
                if (v > max) max = v;
                if (v < min) min = v;
            }
            agg.setAvgHeartRate((int) Math.round((double) sum / bpms.size()));
            agg.setMaxHeartRate(max);
            agg.setMinHeartRate(min);
        }
        touch(agg);
        return dailyAggregateRepository.save(agg);
    }

    private Integer extractBpm(HealthEventRaw r) {
        if (r.getPayload() == null) {
            return null;
        }
        Object v = r.getPayload().get("bpm");
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
```

(`ArrayList`, `List`, `Instant`, `LocalDate`, `LocalDateTime`, `ZoneId` are already imported in this file.)

> Note: the `to` bound is exclusive-ish via `findAll...Between` (Spring Data `Between` is inclusive on both ends; using start-of-next-day as `to` includes the boundary instant at exactly 00:00 of the next day — acceptable for this MVP since a reading at exactly midnight is rare and harmless). The tests pass any-matchers for `from`/`to`, so they don't pin the boundary.

- [ ] **Step 4: Run test to verify it passes**

Run: `"$MVN" test -Dtest=DailyAggregateServiceTest`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/DailyAggregateService.java \
        src/test/java/com/example/dacn2_beserver/service/health/DailyAggregateServiceTest.java
git commit -m "feat(health): recompute daily heart-rate avg/max/min in DailyAggregateService"
```

---

## Task 4: HeartRateService + controller routes

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/HeartRateService.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/controller/HealthController.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/HeartRateServiceTest.java`

**Interfaces:**
- Consumes: `CreateHeartRateRequest`, `HeartRateReadingResponse` (Task 2); `HealthEventRawRepository.save` + `...AndTimeStartAtBetweenOrderByTimeStartAtDesc` (Task 1); `DailyAggregateService.addHeartRate` (Task 3).
- Produces: `HeartRateService.create(String userId, CreateHeartRateRequest) : HeartRateReadingResponse` and `listResponses(String userId, Instant from, Instant to) : List<HeartRateReadingResponse>` — consumed by the controller.

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/HeartRateServiceTest.java`:

```java
package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateHeartRateRequest;
import com.example.dacn2_beserver.dto.health.HeartRateReadingResponse;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartRateServiceTest {

    @Mock HealthEventRawRepository healthEventRawRepository;
    @Mock DailyAggregateService dailyAggregateService;

    HeartRateService service;

    private HeartRateService svc() {
        return new HeartRateService(healthEventRawRepository, dailyAggregateService);
    }

    @Test
    void createSavesRawAndAggregatesAndReturnsBpm() {
        service = svc();
        Instant at = Instant.parse("2026-06-26T08:00:00Z");
        when(healthEventRawRepository.save(any(HealthEventRaw.class)))
                .thenAnswer(inv -> {
                    HealthEventRaw r = inv.getArgument(0);
                    r.setId("r1");
                    return r;
                });

        CreateHeartRateRequest req = CreateHeartRateRequest.builder().bpm(75).measuredAt(at).build();
        HeartRateReadingResponse res = service.create("u1", req);

        // saved a HEART_RATE raw event with bpm in payload
        ArgumentCaptor<HealthEventRaw> captor = ArgumentCaptor.forClass(HealthEventRaw.class);
        verify(healthEventRawRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(EventType.HEART_RATE);
        assertThat(captor.getValue().getPayload().get("bpm")).isEqualTo(75);
        // rolled into aggregate
        verify(dailyAggregateService).addHeartRate(eq("u1"), eq(at), eq(75));
        // response carries bpm + measuredAt
        assertThat(res.getBpm()).isEqualTo(75);
        assertThat(res.getMeasuredAt()).isEqualTo(at);
        assertThat(res.getId()).isEqualTo("r1");
    }

    @Test
    void createDefaultsMeasuredAtToNowWhenNull() {
        service = svc();
        when(healthEventRawRepository.save(any(HealthEventRaw.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateHeartRateRequest req = CreateHeartRateRequest.builder().bpm(66).measuredAt(null).build();
        HeartRateReadingResponse res = service.create("u1", req);

        assertThat(res.getMeasuredAt()).isNotNull();
        assertThat(res.getBpm()).isEqualTo(66);
    }

    @Test
    void listResponsesMapsBpmFromPayload() {
        service = svc();
        Instant a1 = Instant.parse("2026-06-26T08:00:00Z");
        Instant a2 = Instant.parse("2026-06-26T09:00:00Z");
        HealthEventRaw r1 = HealthEventRaw.builder().id("r1").userId("u1").type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(a2).endAt(a2).build())
                .payload(Map.of("bpm", 88)).createdAt(a2).build();
        HealthEventRaw r2 = HealthEventRaw.builder().id("r2").userId("u1").type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(a1).endAt(a1).build())
                .payload(Map.of("bpm", 70)).createdAt(a1).build();
        when(healthEventRawRepository.findAllByUserIdAndTypeAndTimeStartAtBetweenOrderByTimeStartAtDesc(
                eq("u1"), eq(EventType.HEART_RATE), any(), any()))
                .thenReturn(List.of(r1, r2));

        List<HeartRateReadingResponse> out = service.listResponses("u1", a1, a2);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getBpm()).isEqualTo(88);
        assertThat(out.get(0).getMeasuredAt()).isEqualTo(a2);
        assertThat(out.get(1).getBpm()).isEqualTo(70);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$MVN" test -Dtest=HeartRateServiceTest`
Expected: FAIL — `HeartRateService` does not exist (compile error).

- [ ] **Step 3: Create HeartRateService**

`DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/HeartRateService.java`:

```java
package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.CreateHeartRateRequest;
import com.example.dacn2_beserver.dto.health.HeartRateReadingResponse;
import com.example.dacn2_beserver.model.common.TimeRange;
import com.example.dacn2_beserver.model.enums.EventType;
import com.example.dacn2_beserver.model.health.HealthEventRaw;
import com.example.dacn2_beserver.repository.HealthEventRawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Lưu chỉ số nhịp tim (raw event) và cộng vào DailyAggregate.
 * Theo khuôn WaterService.
 */
@Service
@RequiredArgsConstructor
public class HeartRateService {

    private final HealthEventRawRepository healthEventRawRepository;
    private final DailyAggregateService dailyAggregateService;

    public HeartRateReadingResponse create(String userId, CreateHeartRateRequest req) {
        Instant measuredAt = (req.getMeasuredAt() != null ? req.getMeasuredAt() : Instant.now())
                .truncatedTo(ChronoUnit.SECONDS);
        int bpm = req.getBpm();

        HealthEventRaw event = HealthEventRaw.builder()
                .userId(userId)
                .type(EventType.HEART_RATE)
                .time(TimeRange.builder().startAt(measuredAt).endAt(measuredAt).build())
                .payload(Map.of("bpm", bpm))
                .createdAt(Instant.now())
                .build();

        event = healthEventRawRepository.save(event);

        dailyAggregateService.addHeartRate(userId, measuredAt, bpm);

        return toResponse(event);
    }

    public List<HeartRateReadingResponse> listResponses(String userId, Instant from, Instant to) {
        return healthEventRawRepository
                .findAllByUserIdAndTypeAndTimeStartAtBetweenOrderByTimeStartAtDesc(
                        userId, EventType.HEART_RATE, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private HeartRateReadingResponse toResponse(HealthEventRaw event) {
        int bpm = 0;
        if (event.getPayload() != null && event.getPayload().get("bpm") instanceof Number n) {
            bpm = n.intValue();
        }
        Instant measuredAt = event.getTime() != null ? event.getTime().getStartAt() : event.getCreatedAt();
        return HeartRateReadingResponse.builder()
                .id(event.getId())
                .bpm(bpm)
                .measuredAt(measuredAt)
                .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"$MVN" test -Dtest=HeartRateServiceTest`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Add controller routes**

In `HealthController.java`:

1. Add the service dependency field (constructor via `@RequiredArgsConstructor`):

```java
    private final HeartRateService heartRateService;
```

Add imports:
```java
import com.example.dacn2_beserver.service.health.HeartRateService;
```
(`CreateHeartRateRequest`, `HeartRateReadingResponse` are covered by the existing wildcard `import com.example.dacn2_beserver.dto.health.*;`. `Instant`, `List`, `@Valid`, `ApiResponse`, `@AuthenticationPrincipal AuthPrincipal` are already imported.)

2. Add the two routes (place after the SLEEP section):

```java
    // -------- HEART RATE --------

    @PostMapping("/heart-rate")
    public ApiResponse<HeartRateReadingResponse> createHeartRate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateHeartRateRequest req
    ) {
        return ApiResponse.ok(heartRateService.create(principal.userId(), req));
    }

    @GetMapping("/heart-rate")
    public ApiResponse<List<HeartRateReadingResponse>> listHeartRate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ApiResponse.ok(heartRateService.listResponses(principal.userId(), from, to));
    }
```

- [ ] **Step 6: Run the heart tests + compile**

Run: `"$MVN" test -Dtest=HeartRateServiceTest && "$MVN" -o test-compile 2>&1 | tail -5`
Expected: tests PASS and BUILD SUCCESS (controller compiles with the new wiring).

- [ ] **Step 7: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/HeartRateService.java \
        src/main/java/com/example/dacn2_beserver/controller/HealthController.java \
        src/test/java/com/example/dacn2_beserver/service/health/HeartRateServiceTest.java
git commit -m "feat(health): add POST/GET /health/heart-rate endpoints"
```

---

## Task 5: Expose heart-rate in summary + calendar responses

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/SummaryService.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/health/CalendarDaySummaryResponse.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/health/CalendarService.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/SummaryServiceTest.java`

**Interfaces:**
- Consumes: `DailyAggregate.getAvgHeartRate/getMaxHeartRate/getMinHeartRate`.
- Produces: `DailyAggregateResponse` and `CalendarDaySummaryResponse` now carry heart-rate values.

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/health/SummaryServiceTest.java`:

```java
package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.DailyAggregateResponse;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock DailyAggregateRepository dailyAggregateRepository;
    @InjectMocks SummaryService service;

    @Test
    void getByDateMapsHeartRateFields() {
        LocalDate date = LocalDate.parse("2026-06-26");
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(date)
                .avgHeartRate(80).maxHeartRate(100).minHeartRate(60)
                .build();
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.of(agg));

        DailyAggregateResponse res = service.getByDate("u1", date);

        assertThat(res.getAvgHeartRate()).isEqualTo(80);
        assertThat(res.getMaxHeartRate()).isEqualTo(100);
        assertThat(res.getMinHeartRate()).isEqualTo(60);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$MVN" test -Dtest=SummaryServiceTest`
Expected: FAIL — `getAvgHeartRate()` on the response is null (not mapped), assertion fails.

- [ ] **Step 3: Map heart-rate in SummaryService**

In `SummaryService.toResponse`, add three lines to the builder (after `.caloriesOut(...)`):

```java
                .avgHeartRate(nvl(agg.getAvgHeartRate()))
                .maxHeartRate(nvl(agg.getMaxHeartRate()))
                .minHeartRate(nvl(agg.getMinHeartRate()))
```

(`nvl(Integer)` already exists in this class and returns `int`; the response fields are `Integer` — autoboxing applies. A missing reading day → `0`.)

- [ ] **Step 4: Add heart-rate fields to CalendarDaySummaryResponse**

In `CalendarDaySummaryResponse.java`, add after `awakeMinutes`:

```java
    private int avgHeartRate;
    private int maxHeartRate;
    private int minHeartRate;
```

- [ ] **Step 5: Map heart-rate in CalendarService**

In `CalendarService.getDay`, add to the populated-day builder (after `.awakeMinutes(...)`):

```java
                .avgHeartRate(nvl(a.getAvgHeartRate()))
                .maxHeartRate(nvl(a.getMaxHeartRate()))
                .minHeartRate(nvl(a.getMinHeartRate()))
```

And in `emptyDay`, add (after `.awakeMinutes(0)`):

```java
                .avgHeartRate(0)
                .maxHeartRate(0)
                .minHeartRate(0)
```

- [ ] **Step 6: Run test + compile**

Run: `"$MVN" test -Dtest=SummaryServiceTest && "$MVN" -o test-compile 2>&1 | tail -5`
Expected: test PASS and BUILD SUCCESS (CalendarService compiles with the new builder fields).

- [ ] **Step 7: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/health/SummaryService.java \
        src/main/java/com/example/dacn2_beserver/dto/health/CalendarDaySummaryResponse.java \
        src/main/java/com/example/dacn2_beserver/service/health/CalendarService.java \
        src/test/java/com/example/dacn2_beserver/service/health/SummaryServiceTest.java
git commit -m "feat(health): expose heart-rate fields in summary and calendar responses"
```

---

## Task 6: Frontend — save reading + history screen

**Files:**
- Create: `DACN2_FEserver/src/screens/HeartMeasurement/api.ts`
- Modify: `DACN2_FEserver/src/screens/HeartMeasurement/HeartResultScreen.tsx`
- Create: `DACN2_FEserver/src/screens/HeartMeasurement/HeartHistoryScreen.tsx`
- Modify: `DACN2_FEserver/src/navigation/AppStack/BrowserStack.tsx`

**Interfaces:**
- Consumes: BE `POST /health/heart-rate` (Task 4), `GET /health/heart-rate?from&to` (Task 4).
- Produces: none (leaf UI).

- [ ] **Step 1: Create the API helper**

`DACN2_FEserver/src/screens/HeartMeasurement/api.ts`:

```ts
import { api, unwrapApiData } from '../../services/api';

export type HeartRateReading = {
  id: string;
  bpm: number;
  measuredAt: string;
};

export const saveHeartRate = async (
  bpm: number,
  measuredAt: string,
): Promise<void> => {
  await api.post('/health/heart-rate', { bpm, measuredAt });
};

export const fetchHeartRateHistory = async (
  from: string,
  to: string,
): Promise<HeartRateReading[]> => {
  const res = await api.get('/health/heart-rate', { params: { from, to } });
  return (unwrapApiData(res.data) as HeartRateReading[]) ?? [];
};
```

(Verify the relative path to `services/api` matches the other screens — `HeartResultScreen` imports SVGs via `../../assets/...`, so `../../services/api` is correct from `src/screens/HeartMeasurement/`.)

- [ ] **Step 2: POST the reading on HeartResultScreen mount**

In `HeartResultScreen.tsx`, add the imports and a save-on-mount effect. Near the top with other imports:

```tsx
import React, { useEffect, useRef } from 'react';
import { saveHeartRate } from './api';
```

(If `React` is already imported as `import React from 'react'`, change it to include the hooks: `import React, { useEffect, useRef } from 'react';`.)

Inside the component, after `const { bpm } = route.params;`, add:

```tsx
  const savedRef = useRef(false);

  useEffect(() => {
    if (savedRef.current) return;
    savedRef.current = true;
    saveHeartRate(bpm, new Date().toISOString()).catch(err => {
      console.warn('Save heart rate failed:', err);
    });
  }, [bpm]);
```

(Silent save; failure is logged and does not block the result UI, per spec.)

- [ ] **Step 3: Create HeartHistoryScreen**

`DACN2_FEserver/src/screens/HeartMeasurement/HeartHistoryScreen.tsx`:

```tsx
import React, { useEffect, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { theme } from '../../assets/theme';
import { fetchHeartRateHistory, HeartRateReading } from './api';

const statusOf = (bpm: number): { label: string; color: string } => {
  if (bpm < 60) return { label: 'Thấp', color: '#FACC15' };
  if (bpm <= 100) return { label: 'Bình thường', color: '#22C55E' };
  return { label: 'Cao', color: '#EF4444' };
};

const formatWhen = (iso: string): string => {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '--';
  return d.toLocaleString();
};

const HeartHistoryScreen: React.FC = () => {
  const navigation = useNavigation();
  const [readings, setReadings] = useState<HeartRateReading[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const to = new Date();
    const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000);
    fetchHeartRateHistory(from.toISOString(), to.toISOString())
      .then(setReadings)
      .catch(() => setReadings([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.back}>
          <Text style={styles.backText}>←</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Lịch sử nhịp tim</Text>
      </View>

      {!loading && readings.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyIcon}>❤️</Text>
          <Text style={styles.emptyTitle}>Chưa có dữ liệu nhịp tim</Text>
          <Text style={styles.emptyText}>
            Hãy đo nhịp tim để xem lịch sử tại đây.
          </Text>
        </View>
      ) : (
        <FlatList
          data={readings}
          keyExtractor={item => item.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => {
            const s = statusOf(item.bpm);
            return (
              <View style={styles.row}>
                <View>
                  <Text style={styles.bpm}>{item.bpm} BPM</Text>
                  <Text style={styles.when}>{formatWhen(item.measuredAt)}</Text>
                </View>
                <Text style={[styles.status, { color: s.color }]}>{s.label}</Text>
              </View>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: theme.colors.white },
  header: { flexDirection: 'row', alignItems: 'center', padding: 16, gap: 12 },
  back: { width: 40, height: 40, justifyContent: 'center' },
  backText: { fontSize: 24, color: theme.colors.text },
  title: { fontSize: 18, fontWeight: '700', color: theme.colors.text },
  list: { paddingHorizontal: 16 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
  },
  bpm: { fontSize: 16, fontWeight: '700', color: theme.colors.text },
  when: { fontSize: 12, color: '#94A3B8', marginTop: 2 },
  status: { fontSize: 14, fontWeight: '600' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  emptyIcon: { fontSize: 40, marginBottom: 12 },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: theme.colors.text },
  emptyText: { fontSize: 13, color: '#94A3B8', textAlign: 'center', marginTop: 6 },
});

export default HeartHistoryScreen;
```

(Verify `theme.colors.text` and `theme.colors.white` exist — they are used in `HeartResultScreen.tsx`, so they do.)

- [ ] **Step 4: Register the route in BrowserStack**

In `src/navigation/AppStack/BrowserStack.tsx`:

1. Add the import (next to the other Heart imports):
```tsx
import HeartHistoryScreen from '@screens/HeartMeasurement/HeartHistoryScreen';
```
2. Add to `BrowserStackParamList` (after `HeartResult`):
```tsx
  /** Lịch sử nhịp tim */
  HeartHistory: undefined;
```
3. Register the screen (after the `HeartResult` screen line):
```tsx
      <Stack.Screen name="HeartHistory" component={HeartHistoryScreen} />
```

- [ ] **Step 5: Add a "History" entry point on HeartResultScreen**

In `HeartResultScreen.tsx`, add a button that navigates to the history screen. Find the header area (it renders an `ArrowLeftIcon` back button). Add a small touchable near the title or in the scroll content:

```tsx
        <TouchableOpacity onPress={() => navigation.navigate('HeartHistory')}>
          <Text style={{ color: theme.colors.primary, fontWeight: '600', textAlign: 'center', marginTop: 8 }}>
            Xem lịch sử nhịp tim
          </Text>
        </TouchableOpacity>
```

(`navigation`, `theme`, and `TouchableOpacity` are already imported in the file. Place it where it reads naturally — e.g. just above the closing `</ScrollView>`.)

- [ ] **Step 6: Lint and type-check**

Run:
```
cd /d/DATN/DACN2_FEserver
npm run lint 2>&1 | tail -25
npx --no-install tsc --noEmit 2>&1 | grep -E "HeartResultScreen|HeartHistoryScreen|HeartMeasurement/api|BrowserStack" || echo "no type errors in changed files"
```
Expected: no new ESLint errors in the changed files; no tsc errors referencing them. (Pre-existing warnings / unrelated test-file tsc errors are fine.)

- [ ] **Step 7: Commit**

```bash
cd /d/DATN/DACN2_FEserver
git add src/screens/HeartMeasurement/api.ts \
        src/screens/HeartMeasurement/HeartResultScreen.tsx \
        src/screens/HeartMeasurement/HeartHistoryScreen.tsx \
        src/navigation/AppStack/BrowserStack.tsx
git commit -m "feat(heart): persist readings and add heart-rate history screen"
```

---

## Manual verification (after all tasks)

1. Start backend on a non-intercepted network.
2. Measure heart rate in the app → on the result screen, a `POST /health/heart-rate` fires (check backend logs / DB `events_raw` has a HEART_RATE doc with `payload.bpm`).
3. `GET /health/summary/<today>` → `avgHeartRate`/`maxHeartRate`/`minHeartRate` are populated (no longer null).
4. Open Home → `HeartSleepGrid` shows the real average with the correct status. Open Calendar for today → heart rate appears.
5. Measure a second time with a different BPM → summary avg updates to the mean of both, max/min reflect the spread.
6. Open "Xem lịch sử nhịp tim" → both readings listed, newest first, with status labels.
