# Phân tích tình trạng hằng ngày — cá nhân hóa theo mục tiêu

**Ngày:** 2026-06-25
**Phạm vi:** Backend (Spring Boot) + Frontend (React Native)
**Tính năng liên quan:** Theo dõi bước chân (footstep tracking) & phân tích tình trạng (daily analysis)

## Bối cảnh & vấn đề

Endpoint `/ai/daily-analysis` (`AiAnalysisService`) hiện chấm điểm sức khỏe (`readinessScore`) bằng **heuristic dựa trên ngưỡng cứng** (bước ≥ 8000 = +15 điểm...) và **dữ liệu do client gửi lên** trong một `summary` map rời rạc. Service không nhận biết user nào và không đọc dữ liệu thật từ DB.

Trong khi đó:
- `UserGoals.dailySteps`, `dailyWaterMl`, `dailyCaloriesOut` đã được lưu trong DB nhưng phần phân tích **không dùng đến** — điểm số so với mốc cố định thay vì mục tiêu cá nhân của user.
- FE `ActivityCard` hiển thị progress bar bước với target **hardcode 10.000** thay vì lấy `dailySteps` của user.

## Mục tiêu

Biến phân tích thành **cá nhân hóa theo mục tiêu của user**, với dữ liệu **server tự kéo từ DB** dựa trên JWT principal. Giữ engine **heuristic rule-based** (không gọi LLM): nhanh, chạy offline, dễ test.

## Phi mục tiêu (YAGNI)

- Không dùng LLM/Groq cho phân tích này.
- Không thêm cảm biến đếm bước (pedometer) — đó là việc khác.
- Không đổi luồng tracking session (start/pause/resume/end).
- Không thêm goal mới ngoài các goal đã có trong `UserGoals`.

## Kiến trúc

Tách bạch theo đơn vị có một nhiệm vụ rõ ràng:

- **`ReadinessScorer`** — đơn vị thuần (no I/O, không phụ thuộc Spring). Nhận metrics + goals đã resolve, trả về score + action plan + summary. Test độc lập dễ dàng.
- **`AiAnalysisService`** — orchestration: kéo `UserGoals` + `DailyAggregate(userId, date)` từ repository, resolve goal default, gọi `ReadinessScorer`, build `DailyAnalysisResponse`.
- **FE** — các thay đổi độc lập theo từng màn hình.

### Tương thích ngược

`summary` trong request giữ lại là **optional**. Nếu server không tìm thấy `DailyAggregate` cho ngày đó nhưng request có `summary` (FE cũ), thì fallback parse `summary` như logic cũ. FE cũ vẫn chạy được, không vỡ.

## Backend

### Endpoint

`AiController.dailyAnalysis()` thêm `@AuthenticationPrincipal AuthPrincipal principal` và truyền `principal.userId()` xuống service.

```java
@PostMapping(value = "/daily-analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
public ApiResponse<DailyAnalysisResponse> dailyAnalysis(
        @AuthenticationPrincipal AuthPrincipal principal,
        @RequestBody DailyAnalysisRequest req) {
    return ApiResponse.ok(aiAnalysisService.analyzeDaily(principal.userId(), req));
}
```

> Lưu ý: `/ai/daily-analysis` **không** nằm trong danh sách public của `SecurityConfig`, nên đã yêu cầu JWT — principal luôn có sẵn. Không cần đổi cấu hình bảo mật.

### Resolve dữ liệu

`AiAnalysisService.analyzeDaily(String userId, DailyAnalysisRequest req)`:

1. Đọc `User` qua `UserRepository` → lấy `UserGoals` (có thể null hoặc các field null).
2. Parse `req.getDate()` thành `LocalDate` (nếu null/parse lỗi → dùng ngày hôm nay theo timezone của user, như cách `CalendarService` làm).
3. Đọc `DailyAggregate(userId, date)` qua `DailyAggregateRepository.findByUserIdAndDate`.
4. Lấy metrics theo thứ tự ưu tiên:
   - Nếu có `DailyAggregate` → dùng `steps`, `waterMl`, `caloriesOut`, `sleepMinutes` (đổi ra giờ).
   - Nếu không có aggregate nhưng request có `summary` → fallback parse `summary` (logic `extractMetrics`/`walk` hiện tại, giữ lại).
   - Nếu cả hai đều rỗng → metrics rỗng.

### Resolve goal (default chuẩn y tế)

Với mỗi tiêu chí, dùng goal cá nhân nếu có và > 0, ngược lại dùng default:

| Tiêu chí | Goal cá nhân | Default (khi null/≤0) |
|---|---|---|
| Bước | `UserGoals.dailySteps` | 8.000 |
| Nước | `UserGoals.dailyWaterMl` | 2.000 ml |
| Calo ra | `UserGoals.dailyCaloriesOut` | 500 (khớp `DEFAULT_CALORIES_OUT_GOAL`) |
| Ngủ | *(không có goal)* | khoảng lý tưởng 7–9 giờ |

Default luôn > 0 → không bao giờ chia cho 0.

### Công thức chấm điểm (`ReadinessScorer`)

Mỗi tiêu chí đóng góp điểm theo **tỷ lệ đạt mục tiêu** (không còn 3 bậc cứng):

```
ratio  = min(actual / goal, 1.0)        // bước, nước, calo
points = round(ratio * maxPoints)
```

Trọng số (base + tổng = 100):

- Base: **60**
- Bước: tối đa **15** → `round(min(steps/goalSteps, 1) * 15)`
- Nước: tối đa **10** → `round(min(waterMl/goalWaterMl, 1) * 10)`
- Calo ra: tối đa **5** → `round(min(caloriesOut/goalCaloriesOut, 1) * 5)` *(tiêu chí mới — trước đây không chấm calo)*
- Ngủ: tối đa **10** (đặc biệt vì là *khoảng*, không phải càng-cao-càng-tốt):
  - trong 7–9h → 10 điểm
  - trong 6–10h → 5 điểm
  - ngoài ra → 2 điểm

Một tiêu chí có metric null → không cộng điểm cho tiêu chí đó (đóng góp 0).

Clamp cuối: `max(50, min(95, score))`.

Khi **không có dữ liệu nào** (metrics rỗng hoàn toàn): trả về **78** (điểm tham khảo mặc định, giữ như hiện tại).

### Action plan cá nhân hóa

Tham chiếu số cụ thể từ goal + actual.

- **Bước — chưa đạt:** "Còn {goal − actual} bước nữa để đạt mục tiêu {goal} bước của bạn — thử đi bộ 10–15 phút sau bữa ăn."
- **Bước — đã đạt:** "Bạn đã đạt mục tiêu {goal} bước hôm nay 👏 Duy trì vận động đều đặn."
- **Nước — chưa đạt:** "Còn khoảng {goal − actual}ml nữa để đạt mục tiêu {goal}ml — uống rải đều theo từng khung giờ."
- **Nước — đã đạt:** "Bạn đã đủ nước hôm nay. Tiếp tục uống rải đều trong ngày."
- **Ngủ — ngoài khoảng:** "Điều chỉnh giờ ngủ về 7–9 giờ và tránh caffeine trước khi ngủ." (giữ như cũ)
- **Ngủ — trong khoảng:** "Giữ lịch ngủ ổn định và tránh caffeine trước giờ ngủ." (giữ như cũ)

`summary` văn bản tương tự hiện tại nhưng nêu rõ điểm theo mục tiêu cá nhân.

`targetUsers`, `missingForHealthApp`, `optimizations`, `disclaimer`: **giữ nguyên** như hiện tại (đây là phần mô tả app cố định, không cá nhân hóa).

### Đơn vị `ReadinessScorer` (thuần)

```java
record Goals(int steps, int waterMl, int caloriesOut)        // đã resolve default
record DailyMetrics(Double steps, Double waterMl, Double caloriesOut, Double sleepHours)
record ScoreResult(int score, List<String> actionPlan, String summary)

ScoreResult score(DailyMetrics m, Goals g, String dateLabel)
```

Không I/O, không phụ thuộc Spring.

## Frontend

### AI Analysis Screen

`AiAnalysisScreen.tsx` — bỏ gửi `summary`, chỉ gửi `date`:

```ts
const res = await api.post('/ai/daily-analysis', { date: selectedDate });
```

`fallbackAnalysis` giữ nguyên (dùng khi network lỗi). Cấu trúc hiển thị score + action plan không đổi — chỉ nội dung trở nên cá nhân hóa.

### ActivityCard target thật

`ActivityCard.tsx` **không cần sửa** — đã nhận prop `targetSteps`/`targetCalories` (default 10000/1000). Chỉ sửa caller `HomeScreen.tsx` truyền target từ `UserContext`:

```tsx
<ActivityCard
  steps={...}
  calories={...}
  targetSteps={user?.goals?.dailySteps ?? 10000}
  targetCalories={user?.goals?.dailyCaloriesOut ?? 1000}
/>
```

**Cần xác minh khi viết plan:** `goals` (`dailySteps`, `dailyCaloriesOut`) có nằm trong shape `user` mà `/auth/me` trả về và trong `UserContext` không. Nếu chưa, plan thêm bước expose các field này trong `UserResponse` (BE) và type của `UserContext` (FE).

## Xử lý lỗi (Backend)

- **User không tồn tại** → `ApiException(ErrorCode.USER_NOT_FOUND)` (pattern có sẵn).
- **Không có `DailyAggregate` cho ngày** → metrics rỗng; nếu request có `summary` thì fallback parse summary; ngược lại trả score 78. **Không ném lỗi.**
- **Goal null/≤0** → resolve sang default (luôn > 0, không chia cho 0).
- **`date` null/parse lỗi** → dùng ngày hôm nay theo timezone user.

## Testing

- **Unit test `ReadinessScorer`** (thuần): đạt 100% goal; đạt một phần; vượt goal (ratio clamp về 1); tất cả metric null (score 78); ngủ trong khoảng / ngoài khoảng; tiêu chí calo mới.
- **Service test `AiAnalysisService`**: mock `UserRepository` + `DailyAggregateRepository`:
  - có aggregate → chấm theo dữ liệu DB;
  - không có aggregate + có `summary` → fallback parse summary;
  - user không có goal → dùng default;
  - user không tồn tại → ném `USER_NOT_FOUND`.
- **FE:** kiểm thử thủ công (project không có test cho các screen này).

## Các file dự kiến chạm

**Backend (`DACN2_BEserver`):**
- `controller/AiController.java` — thêm principal.
- `service/ai/AiAnalysisService.java` — orchestration, đọc repo, resolve goal.
- `service/ai/ReadinessScorer.java` *(mới)* — logic chấm điểm thuần.
- `dto/user/UserResponse.java` *(có thể)* — expose `dailySteps`/`dailyCaloriesOut` nếu chưa có.
- test tương ứng dưới `src/test`.

**Frontend (`DACN2_FEserver`):**
- `src/screens/AppScreen/AIAnalysis/AiAnalysisScreen.tsx` — chỉ gửi `date`.
- `src/screens/AppScreen/Home/HomeScreen.tsx` — truyền target thật.
- `src/context/UserContext.tsx` & types *(có thể)* — expose goals nếu chưa có.
