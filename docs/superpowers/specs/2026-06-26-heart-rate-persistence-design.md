# Đường lưu trữ nhịp tim (Heart-rate persistence)

**Ngày:** 2026-06-26
**Phạm vi:** Backend (Spring Boot) + Frontend (React Native)
**Tính năng liên quan:** Đo nhịp tim — Spec 1/2 (Spec 2 sau này: PPG camera thật)

## Bối cảnh & vấn đề

Chức năng đo nhịp tim hiện gần như chỉ là vỏ:
- **FE:** `HeartMeasurementScreen` hiển thị camera + đèn flash nhưng **không xử lý frame**; BPM sinh từ `Math.random()` + sóng sin giả. Kết quả (`HeartResultScreen` nhận `bpm` qua route param) **không được lưu** — không gọi API nào.
- **BE:** `EventType.HEART_RATE`, `DailyAggregate.avgHeartRate/maxHeartRate/minHeartRate`, `HealthEventRaw` đều **có sẵn nhưng chưa bao giờ được ghi**. Không có endpoint nhận nhịp tim, không có `addHeartRate()`, và `SummaryService`/`CalendarService` **không map** avgHeartRate ra response → Home/Calendar luôn hiển thị null cho tim.

## Mục tiêu

Một lần đo nhịp tim được **lưu thật**, cộng vào `DailyAggregate` (avg/max/min chính xác trong ngày), hiển thị ở Home/Calendar, và có **màn lịch sử** các lần đo. **Độc lập với cách đo** — nguồn BPM hiện tại (giả lập) vẫn dùng tạm; Spec 2 sẽ thay bằng PPG camera thật, cắm vào đúng đường lưu này.

## Phi mục tiêu (YAGNI)

- Không làm PPG camera / xử lý frame (đó là Spec 2).
- Không làm generic event-ingest endpoint (dùng endpoint chuyên biệt).
- Không thêm heart-rate goal vào UserGoals.
- Không đụng heart rate trong WorkoutSession (ngoài phạm vi).

## Quyết định thiết kế (đã chốt)

- **Endpoint chuyên biệt** `POST /health/heart-rate` (theo khuôn `/health/water`, `/health/sleep`), không dùng generic ingest.
- **Lưu raw + recompute chính xác:** mỗi lần đo lưu 1 `HealthEventRaw` (type=HEART_RATE, payload `{bpm}`); `addHeartRate` đọc lại MỌI reading trong ngày → tính avg/max/min (không dùng running-average). Không tạo model mới.
- **FE:** lưu kết quả + Home/Calendar hiện số thật + thêm màn lịch sử (cần endpoint GET list).

## Kiến trúc & luồng

```
FE HeartResultScreen → POST /health/heart-rate {bpm, measuredAt}
  → HeartRateService.create: lưu 1 HealthEventRaw(type=HEART_RATE, payload={bpm})
      → DailyAggregateService.addHeartRate(userId, measuredAt, bpm):
          đọc lại mọi HEART_RATE reading trong "ngày của user" → avg=round(mean), max, min → lưu DailyAggregate
      → trả HeartRateReadingResponse
FE Home/Calendar → GET /health/summary|calendar → avgHeartRate giờ có giá trị (sau khi sửa map)
FE HeartHistoryScreen → GET /health/heart-rate?from&to → list các lần đo
```

Đơn vị (theo đúng pattern Water/Sleep đã có): `HeartRateService` ↔ `WaterService`; `addHeartRate` ↔ `addWater` (nhưng recompute thay vì cộng dồn).

## Backend

### Lưu trữ
Dùng `HealthEventRaw` sẵn có (collection `events_raw`). Mỗi lần đo:
- `type = EventType.HEART_RATE`
- `payload = { "bpm": <int> }`
- `time = TimeRange(startAt = measuredAt, endAt = measuredAt)`
- `createdAt = now`

Repo `HealthEventRawRepository` đã có `findAllByUserIdAndTypeAndCreatedAtBetween(userId, type, from, to)`. `addHeartRate` truy vấn các reading của "ngày user" theo khoảng `[startOfDay, endOfDay)` (theo timezone user, dùng `time.startAt` của reading; nếu cần truy vấn theo `time.startAt` mà repo chưa hỗ trợ, thêm một method repo `findAllByUserIdAndTypeAndTimeStartAtBetween`).

### DTO (mới)
```java
// CreateHeartRateRequest
@NotNull @Min(30) @Max(230) private Integer bpm;
private Instant measuredAt;   // null → now

// HeartRateReadingResponse
private String id;
private int bpm;
private Instant measuredAt;
```

### HeartRateService (mới) — theo khuôn WaterService
- `create(userId, req)`: `measuredAt = req.getMeasuredAt() != null ? req : now`; lưu `HealthEventRaw`; gọi `dailyAggregateService.addHeartRate(userId, measuredAt, bpm)`; trả `HeartRateReadingResponse`.
- `listResponses(userId, from, to)`: truy vấn HEART_RATE readings trong khoảng, map ra `HeartRateReadingResponse` (đọc `bpm` từ payload), sắp theo thời gian giảm dần.

### DailyAggregateService.addHeartRate (mới)
```java
public DailyAggregate addHeartRate(String userId, Instant measuredAt, int bpm)
```
- Xác định `date` theo timezone user (như `addWater`).
- Đọc mọi HEART_RATE reading của ngày đó từ repo, trích `bpm` từ payload (an toàn kiểu Number), gộp với reading vừa lưu nếu cần.
- `avg = round(mean)`, `max`, `min` → set lên `DailyAggregate`; `touch(agg)`; save.
- (Highlight nhịp tim: tuỳ chọn, MVP có thể bỏ qua để giữ gọn.)

### Sửa hiển thị thiếu
- `SummaryService.toResponse`: thêm `.avgHeartRate(nvl(agg.getAvgHeartRate())).maxHeartRate(...).minHeartRate(...)`.
- `CalendarDaySummaryResponse`: thêm 3 field `avgHeartRate/maxHeartRate/minHeartRate`.
- `CalendarService.getDay` + `emptyDay`: map 3 field tim.

### Controller (thêm vào HealthController)
```java
@PostMapping("/heart-rate")
public ApiResponse<HeartRateReadingResponse> createHeartRate(principal, @Valid @RequestBody CreateHeartRateRequest req)

@GetMapping("/heart-rate")
public ApiResponse<List<HeartRateReadingResponse>> listHeartRate(principal, @RequestParam Instant from, @RequestParam Instant to)
```
`/health/**` đã yêu cầu JWT — không sửa SecurityConfig.

## Frontend

### Lưu kết quả — HeartResultScreen.tsx
- Trong `useEffect` chạy một lần (có cờ chống double-call): `POST /health/heart-rate` với `{ bpm, measuredAt: new Date().toISOString() }`.
- Lưu lặng lẽ; lỗi → log + banner nhỏ "Chưa đồng bộ", KHÔNG chặn hiển thị kết quả.
- BPM nguồn vẫn từ route param (giả lập hiện tại) — Spec 2 thay sau.

### Home/Calendar
Không sửa code: đã đọc `avgHeartRate`. Khi BE map ra số thật, `HeartSleepGrid` (Home) và `DailySummary` (Calendar) tự hiển thị.

### Màn lịch sử mới — HeartHistoryScreen.tsx
- Theo style `FootStepHistoryScreen`. Gọi `GET /health/heart-rate?from&to` (mặc định 30 ngày gần nhất).
- List: BPM + thời gian đo + nhãn trạng thái (Thấp <60 / Bình thường 60–100 / Cao >100).
- Trạng thái rỗng: "Chưa có dữ liệu nhịp tim".
- Thêm route `HeartHistory: undefined` vào `BrowserStackParamList` + đăng ký `<Stack.Screen>`; thêm lối vào (nút "Lịch sử" trên màn đo hoặc màn kết quả).

### Service helper (FE)
Một file nhỏ (ví dụ trong `src/screens/HeartMeasurement/api.ts` hoặc `src/services`) export `saveHeartRate({bpm, measuredAt})` và `fetchHeartRateHistory(from, to)` dùng axios `api`.

## Xử lý lỗi

- BE: `bpm` ngoài 30–230 → 400 (`@Min/@Max` + `@Valid`). `measuredAt` null → `Instant.now()`. User không tồn tại → như pattern hiện có (`requireUser`).
- FE: POST lỗi → không chặn xem kết quả (banner nhẹ). Màn lịch sử lỗi/rỗng → "Chưa có dữ liệu".

## Testing

- **BE `DailyAggregateServiceTest.addHeartRate`** (Mockito): 1 reading → avg=max=min=bpm; nhiều reading (vd 60/80/100) → avg=80, max=100, min=60; gọi lại (recompute) không đổi kết quả (idempotent).
- **BE `HeartRateServiceTest`** (Mockito): `create` lưu raw + gọi `addHeartRate` + trả response đúng bpm; `listResponses` map `bpm` từ payload đúng, sắp xếp giảm dần.
- **BE `SummaryServiceTest`**: `toResponse` map `avgHeartRate/maxHeartRate/minHeartRate` (hiện thiếu) — không null khi aggregate có giá trị.
- **FE:** lint + `tsc --noEmit` trên file đổi/mới (không có test cho screen).

## Các file dự kiến chạm

**Backend (`DACN2_BEserver`):**
- `dto/health/CreateHeartRateRequest.java` *(mới)*
- `dto/health/HeartRateReadingResponse.java` *(mới)*
- `service/health/HeartRateService.java` *(mới)*
- `service/health/DailyAggregateService.java` — `addHeartRate`
- `repository/HealthEventRawRepository.java` — có thể thêm query theo `time.startAt`
- `controller/HealthController.java` — 2 route mới
- `service/health/SummaryService.java` — map heart fields
- `service/health/CalendarService.java` + `dto/health/CalendarDaySummaryResponse.java` — thêm + map heart fields
- tests: `DailyAggregateServiceTest`, `HeartRateServiceTest`, `SummaryServiceTest`

**Frontend (`DACN2_FEserver`):**
- `src/screens/HeartMeasurement/HeartResultScreen.tsx` — lưu kết quả
- `src/screens/HeartMeasurement/HeartHistoryScreen.tsx` *(mới)* + style
- `src/screens/HeartMeasurement/api.ts` *(mới)* — saveHeartRate / fetchHeartRateHistory
- `src/navigation/AppStack/BrowserStack.tsx` — route HeartHistory
- lối vào lịch sử (HeartMeasurementScreen hoặc HeartResultScreen)
