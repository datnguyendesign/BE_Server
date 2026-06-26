# Calories Scan AI — Nhận diện món + nguyên liệu + calo có căn cứ

**Ngày:** 2026-06-26
**Phạm vi:** End-to-end 3 tầng — `DACN2_AIserver` (FastAPI), `DACN2_BEserver` (Spring Boot), `DACN2_FEserver` (React Native).
**Tính năng liên quan:** Calories Scan (đã chạy thật: quét ảnh → presign S3 → `/nutrition/analyze` → confirm log). Spec này nâng cấp phần nhận diện để trả thêm nguyên liệu + dinh dưỡng ước lượng có căn cứ.

## Bối cảnh & vấn đề

Hiện `/food-image` (AI server) chỉ trả `{label, score}`. Calo/macro đến từ catalog `FoodItem` của Spring Boot — nhưng catalog mới seed **17 món**, nên đa số ảnh quét được rơi vào nhánh **UNKNOWN**: FE hiện tên-mã món nhưng **không có calo, không có nguyên liệu**. Mục tiêu người dùng: "sau khi quét được ảnh, AI nhận diện và trả về **tên món + nguyên liệu dự đoán + lượng calo một cách dự đoán có căn cứ**."

Khối building-block đã có sẵn trong `DACN2_AIserver`: EfficientNet-B0 (Food-101, `artifacts/best.pt`), CLIP router, Ollama LLM (`llama3.2:3b` tại `:11434`). Ollama hiện **chỉ** được nối vào `/chat`, **chưa** vào pipeline food-image. Khoảng trống cần lấp: nối Ollama vào food-image để sinh nguyên liệu + calo, rồi cho dữ liệu này chảy qua Spring Boot tới FE.

## Mục tiêu

Quét 1 ảnh món ăn → trả về: **tên món** (EfficientNet), **nguyên liệu dự đoán** + **calo & macro ước lượng** theo **khẩu phần chuẩn** (Ollama LLM), kèm **disclaimer** rằng đây là ước lượng. Suy giảm mượt khi Ollama không sẵn sàng.

## Quyết định thiết kế (đã chốt qua brainstorming)

1. **Phạm vi:** cả 3 tầng end-to-end, một spec, làm tuần tự.
2. **Cơ chế grounding:** Ollama **ước lượng** dinh dưỡng theo **khẩu phần chuẩn** (vd "1 tô ~400g") + ghi **disclaimer**. Không cần seed bảng tra 101 món.
3. **API shape:** **mở rộng chính** `/food-image` (thêm field `nutrition` optional vào `FoodImageResponse`), **không** thêm endpoint mới. `/chat` không bị ảnh hưởng vì gọi `food_pipeline.analyze()` riêng, không qua route `/food-image`.
4. **Ollama lỗi/down/timeout/JSON hỏng → graceful degrade:** vẫn trả tên món + score, `nutrition=null`, message báo "đã nhận diện món nhưng chưa ước lượng được dinh dưỡng". Không tầng nào throw vì Ollama.
5. **Nguồn calo khi món vừa KNOWN (có trong catalog) vừa có nutrition Ollama:** **catalog trước, Ollama bổ sung** — KNOWN giữ calo/macro từ catalog (đáng tin, cố định), lấy **ingredients** từ Ollama; UNKNOWN dùng **toàn bộ** nutrition Ollama.
6. **Ollama chỉ gọi cho top-1** (1 lần/quét, tránh chậm). Candidate hạng 2–3 nếu UNKNOWN vẫn trống như hiện tại — hợp lý vì FE chỉ hiển thị chi tiết đầy đủ cho món primary/được chọn.

## Phi mục tiêu (YAGNI)

- Không seed thêm catalog `FoodItem` (vẫn 17 món; UNKNOWN giờ đã có ước lượng Ollama).
- Không đổi luồng presign S3, confirm log, daily aggregate.
- Không xử lý FastAPI offline (giữ nguyên `AI_SERVICE_ERROR` hiện có — ngoài phạm vi).
- Không thêm endpoint AI mới; không đụng `/chat`, BLIP, vision_router.
- Không gọi Ollama cho candidate hạng 2–3.
- Không thêm UI lịch sử quét (ngoài phạm vi).

## Luồng dữ liệu end-to-end

```
FE CaloriesScanScreen
  → POST /nutrition/analyze {objectKey}
    → BE NutritionAnalyzeService → AiFoodClient.predictFoodByUrl(url)
      → AI POST /api/v1/inference/food-image {image_url}
         ① fetch ảnh → ② CLIP router (food?) → ③ EfficientNet top-3 (label + score)
         → ④ nếu is_food: estimate_nutrition(top1_label) qua Ollama
              → {dish_name, ingredients[], calories, protein_g, carbs_g, fat_g, portion, disclaimer}
              → lỗi/down → None
         → trả FoodImageResponse { status, is_food, message, predictions[], nutrition|null }
    ← BE map: KNOWN → calo catalog + ingredients Ollama; UNKNOWN top-1 → toàn bộ nutrition Ollama
  ← FE ResultSheet: tên món + calo + macros + KHỐI NGUYÊN LIỆU (mới) + disclaimer (note)
```

---

## Tầng AI server (`DACN2_AIserver`)

### Schema mở rộng — `serve/app/schemas/food_image.py`

`predictions` **giữ nguyên** (backward compatible). Thêm `NutritionEstimate` + field `nutrition` optional.

```python
from typing import List, Optional
from pydantic import BaseModel, Field


class FoodPrediction(BaseModel):
    label: str
    score: float


class NutritionEstimate(BaseModel):
    dish_name: str            # tên món người-đọc-được, tiếng Việt (vd "Phở bò")
    ingredients: List[str]    # nguyên liệu dự đoán
    calories: int             # kcal cho khẩu phần chuẩn
    protein_g: float
    carbs_g: float
    fat_g: float
    portion: str              # mô tả khẩu phần chuẩn (vd "1 tô ~400g")
    disclaimer: str           # câu ghi rõ đây là ước lượng


class FoodImageRequest(BaseModel):
    image_url: str


class FoodImageResponse(BaseModel):
    status: str
    is_food: bool
    message: str
    predictions: List[FoodPrediction] = Field(default_factory=list)
    nutrition: Optional[NutritionEstimate] = None  # null khi non-food hoặc Ollama không sẵn sàng
```

### Module sinh dinh dưỡng — `serve/app/domain/food_nutrition.py` *(mới)*

**Một việc:** label Food-101 → `NutritionEstimate | None`. Không biết HTTP/CLIP.

- Hàm `estimate_nutrition(top1_label: str) -> Optional[NutritionEstimate]`.
- Dùng lại `infra/llm_ollama.OllamaClient` (đã có) gọi `{OLLAMA_BASE_URL}/api/chat`, model `OLLAMA_MODEL`.
- Prompt yêu cầu Ollama trả **strict JSON** đúng các field của `NutritionEstimate`, cho **1 khẩu phần chuẩn**, **disclaimer tiếng Việt**, **không markdown fences**. Truyền `top1_label` (Food-101, vd `pho`, `pad_thai`) làm input; yêu cầu `dish_name` là tên tiếng Việt người-đọc-được.
- Parse/repair JSON theo cùng cách `domain/llm_engine.py` đang làm (strip fences, `json.loads`, validate bằng `NutritionEstimate(**data)`).
- **Graceful degrade:** bọc toàn bộ trong try/except — Ollama down, timeout, JSON hỏng, thiếu field, validate fail → log warning + `return None`. **Không throw.**

### Route — `serve/app/api/v1/routes/inference.py` (`POST /food-image`)

- Giữ luồng hiện tại: fetch → CLIP router → nếu food, `food_pipeline.analyze(image, top_k=3)`.
- **Mới:** nếu `is_food` và có predictions, lấy `top1_label = predictions[0].label`, gọi `estimate_nutrition(top1_label)` → gắn vào `nutrition`.
- Nếu `estimate_nutrition` trả `None`: `nutrition=None`, set `message` báo đã nhận diện món nhưng chưa ước lượng được dinh dưỡng (vẫn `status="success"`, `is_food=true`).
- Nếu non-food: `nutrition=None`, luồng như cũ.
- **Không** thêm `require_llm_ready` ở đầu route — để route vẫn chạy (degrade) khi LLM chưa sẵn sàng.

### Testing (AI server)

Không có pytest sẵn. Thêm 1 file test thuần Python cho `food_nutrition.estimate_nutrition` với `OllamaClient` được mock:
- JSON hợp lệ → trả `NutritionEstimate` đúng field.
- JSON có ```` ```json ```` fences → vẫn parse được.
- JSON hỏng / thiếu field / `OllamaClient` raise exception → trả `None` (không throw).

Verify tích hợp thủ công: `GET /health` (llm_ready), rồi `curl` `/food-image` với 1 ảnh thật → xác nhận `nutrition` có giá trị; tắt Ollama → xác nhận `nutrition=null` và request vẫn 200.

---

## Tầng Spring Boot (`DACN2_BEserver`)

### DTO nhận từ AI — `dto/ai/AiFoodPredictResponse.java`

Thêm nested `Nutrition` + field `nutrition` (nullable). Tên field map đúng JSON snake_case của AI.

```java
@JsonProperty("nutrition")
private Nutrition nutrition;   // nullable

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public static class Nutrition {
    @JsonProperty("dish_name")  private String dishName;
    private List<String> ingredients;
    private Integer calories;
    @JsonProperty("protein_g")  private Double proteinG;
    @JsonProperty("carbs_g")    private Double carbsG;
    @JsonProperty("fat_g")      private Double fatG;
    private String portion;
    private String disclaimer;
}
```

### Response ra FE — `dto/nutrition/NutritionAnalyzeResponse.java`

Bổ sung vào `Candidate` các field FE đã đọc sẵn (xem `buildFoodAnalysis` ở FE): `name`, `calories`, `protein`, `carbs`, `fat`, `serving`, `aiInsight`, `ingredients`. Giữ `code`, `score`, `status`, `foodItem` như cũ.

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public static class Candidate {
    private String code;
    private Double score;
    private CandidateStatus status;        // KNOWN / UNKNOWN
    private FoodItemSnapshot foodItem;      // nullable

    // Mới — dữ liệu hiển thị hợp nhất (catalog hoặc Ollama)
    private String name;
    private Integer calories;
    private Integer protein;
    private Integer carbs;
    private Integer fat;
    private String serving;
    private String aiInsight;               // = disclaimer của Ollama (nếu có)
    private List<String> ingredients;       // từ Ollama
}
```

> Lưu ý: macro Ollama là `double` (g), `FoodItemSnapshot` dùng `Integer`. Khi đổ từ Ollama, làm tròn `Math.round(...)` về `Integer` để khớp kiểu Candidate và tránh hiển thị thập phân dài ở FE.

### Logic — `service/nutrition/NutritionAnalyzeService.analyzeByImageUrl()`

Giữ khung hiện tại (lặp top-3 predictions, match catalog, ngưỡng `confident-threshold:0.60`, set `primaryCandidate`/`message`). Bổ sung khi build mỗi `Candidate`:

- **KNOWN** (khớp `FoodItem`): giữ `foodItem` snapshot **và** set các field hiển thị từ catalog (`name`=item.name, `calories`/`protein`/`carbs`/`fat`=catalog). **Nếu** đây là candidate **top-1** và `ai.getNutrition() != null`: set `ingredients` = `nutrition.ingredients` (bổ sung nguyên liệu mà catalog không có). Không ghi đè calo/macro catalog.
- **UNKNOWN** và là candidate **top-1** và `ai.getNutrition() != null`: đổ toàn bộ từ Ollama — `name`=`nutrition.dishName` (fallback `code`), `calories`=`nutrition.calories`, `protein/carbs/fat`=`Math.round(nutrition.proteinG/carbsG/fatG)`, `serving`=`nutrition.portion`, `ingredients`=`nutrition.ingredients`, `aiInsight`=`nutrition.disclaimer`, `status`=UNKNOWN giữ nguyên.
- **UNKNOWN không phải top-1** (hạng 2–3): giữ nguyên hành vi cũ (trống nutrition) — Ollama không chạy cho các hạng này.

`primaryCandidate` và `message` theo logic ngưỡng hiện tại, không đổi.

### Testing (Spring Boot)

Unit test `NutritionAnalyzeService` (mock `AiFoodClient` + `FoodItemRepository`):
- AI trả top-1 KNOWN + `nutrition` có ingredients → candidate giữ calo catalog, có `ingredients` từ Ollama.
- AI trả top-1 UNKNOWN + `nutrition` → candidate có `name`/`calories`/macros/`serving`/`ingredients`/`aiInsight` từ Ollama, `status`=UNKNOWN.
- AI trả `nutrition=null` → không NPE; candidate UNKNOWN trống nutrition như cũ, KNOWN vẫn có calo catalog.
- `is_food=false` → không candidate, không đụng nutrition.

> Chạy test theo workaround truststore đã ghi trong memory (Avast SSL + Maven wrapper). Lệnh: `./mvnw test -Dtest=NutritionAnalyzeServiceTest`.

---

## Tầng Frontend (`DACN2_FEserver`)

FE đã chờ sẵn phần lớn: `NutritionCandidateDto` có `name/kcal/calories/protein/carbs/fat/serving/aiInsight`, và `buildFoodAnalysis()` fallback đúng các tên BE trả. **Gap duy nhất:** nguyên liệu chưa được nhận và hiển thị.

### `src/screens/CaloriesScan/CaloriesScanScreen.tsx`

- Thêm `ingredients?: string[] | null` vào type `NutritionCandidateDto`.
- Trong `buildFoodAnalysis`, thêm `ingredients: candidate.ingredients ?? []` vào object `FoodAnalysis` trả về.

### `src/components/CaloriesScan/ResultSheet/ResultSheet.tsx`

- Thêm `ingredients?: string[]` vào type `FoodAnalysis`.
- Trong nhánh `result ?` (chế độ chi tiết), render **khối "Nguyên liệu dự đoán"** đặt **giữa `macroRow` và `insightBox`**: tiêu đề + danh sách `result.ingredients` (chip hoặc dòng "• <tên>"). **Ẩn khối** nếu `ingredients` rỗng/undefined (degrade khi Ollama down).
- `disclaimer` đã hiển thị qua `note`/`insightBox` sẵn có (`aiInsight` → `note`), không cần thêm.

### Testing (FE)

Không có test tự động cho UI. Verify: `npx tsc --noEmit` sạch trên file đổi + `npm run lint` không lỗi mới + xem trên thiết bị thật: quét 1 món → thấy tên + nguyên liệu + calo + macros + disclaimer; tắt Ollama → vẫn thấy tên món, khối nguyên liệu ẩn, không crash.

> Build/chạy FE theo memory: giữ `API_BASE_URL=localhost:8080`, chạy device qua USB + `adb reverse`. Stage **chỉ** 2 file đổi, không `git add -A` (FE có churn sẵn).

---

## Các file dự kiến chạm

**AI server (`DACN2_AIserver`):**
- `serve/app/schemas/food_image.py` — thêm `NutritionEstimate` + field `nutrition`.
- `serve/app/domain/food_nutrition.py` *(mới)* — `estimate_nutrition(label) -> NutritionEstimate | None`.
- `serve/app/api/v1/routes/inference.py` — gọi `estimate_nutrition` cho top-1 trong `/food-image`.
- test mới cho `food_nutrition`.

**Spring Boot (`DACN2_BEserver`):**
- `dto/ai/AiFoodPredictResponse.java` — nested `Nutrition` + field.
- `dto/nutrition/NutritionAnalyzeResponse.java` — field hiển thị mới trong `Candidate`.
- `service/nutrition/NutritionAnalyzeService.java` — đổ nutrition (catalog-trước, Ollama-bổ sung; UNKNOWN top-1 dùng Ollama).
- test `NutritionAnalyzeServiceTest`.

**Frontend (`DACN2_FEserver`):**
- `src/screens/CaloriesScan/CaloriesScanScreen.tsx` — nhận `ingredients`.
- `src/components/CaloriesScan/ResultSheet/ResultSheet.tsx` — khối nguyên liệu.

## Xử lý lỗi (tổng hợp)

| Tình huống | Hành vi |
|---|---|
| Ollama down/timeout/JSON hỏng | AI `nutrition=null`; BE KNOWN dùng catalog, UNKNOWN trống; FE ẩn khối nguyên liệu + vẫn hiện tên món. Không throw. |
| FastAPI down | Giữ nguyên `AI_SERVICE_ERROR` hiện có (ngoài phạm vi). |
| Non-food | `is_food=false`, `nutrition` bỏ qua, luồng như cũ. |
| Món KNOWN + có nutrition Ollama | Calo/macro từ catalog, ingredients từ Ollama. |
| Món UNKNOWN top-1 + nutrition | Toàn bộ từ Ollama, status=UNKNOWN. |
| UNKNOWN hạng 2–3 | Trống nutrition (Ollama không chạy). |

## Phụ thuộc giữa các tầng

Ba tầng phải đi cùng nhau: schema AI (`nutrition`) → DTO + service Spring Boot → FE đọc `ingredients`. Thứ tự thực thi đề xuất: AI server → Spring Boot → FE (mỗi tầng có deliverable test được riêng, nhưng giá trị end-to-end chỉ thấy khi cả ba xong).
