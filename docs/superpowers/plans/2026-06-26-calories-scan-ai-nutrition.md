# Calories Scan AI Nutrition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After scanning a food photo, return the dish name (EfficientNet), predicted ingredients, and a grounded calorie/macro estimate (Ollama LLM) end-to-end across the FastAPI AI server, Spring Boot backend, and React Native app.

**Architecture:** Extend the existing `/food-image` endpoint to call Ollama for the top-1 EfficientNet label, producing a `nutrition` object (dish_name, ingredients, calories, macros, portion, disclaimer). Spring Boot maps this into its `Candidate` DTO using a catalog-first / Ollama-supplement rule. The FE renders a new "predicted ingredients" block; it already consumes calories/macros/serving/insight fields.

**Tech Stack:** FastAPI (Python 3.10, Pydantic, httpx, Ollama `llama3.2:3b`), Spring Boot 3.5 (Java 17, Lombok, Jackson, JUnit/Mockito), React Native 0.82 (TypeScript).

## Global Constraints

- Spec: `DACN2_BEserver/docs/superpowers/specs/2026-06-26-calories-scan-ai-nutrition-design.md`.
- AI `/food-image` MUST stay backward compatible: keep `predictions: [{label, score}]` unchanged; `nutrition` is an additive optional field.
- Ollama failure (down/timeout/bad JSON/missing field) MUST degrade gracefully — return `nutrition=null`, never throw. No tier fails because Ollama is unavailable.
- Ollama is called for **top-1 only**, once per scan.
- Catalog-first: a KNOWN candidate keeps its catalog calories/macros; Ollama only supplies `ingredients` for it. An UNKNOWN top-1 uses the full Ollama nutrition. UNKNOWN rank 2–3 stay empty.
- AI server JSON uses snake_case (`dish_name`, `protein_g`, `carbs_g`, `fat_g`); Spring Boot maps via `@JsonProperty`.
- Ollama macros are grams as `double`; Spring Boot rounds to `Integer` via `Math.round(...)` before placing in `Candidate`.
- Food-101 labels are snake_case strings (e.g. `pho`, `pad_thai`, `hamburger`).
- AI server: run uvicorn from inside `serve/`. No pytest currently installed — the AI test task adds it and runs `python -m pytest`.
- Spring Boot tests run via the Maven wrapper using the truststore workaround (Avast SSL) recorded in memory `be-test-toolchain-workaround.md`.
- FE: do NOT `git add -A` (heavy pre-existing churn) — stage only the two named files. Keep `API_BASE_URL=localhost:8080`.
- Execution order: AI server → Spring Boot → FE.

---

## File Structure

**AI server (`DACN2_AIserver`):**
- `serve/app/schemas/food_image.py` — add `NutritionEstimate` model + optional `nutrition` field on `FoodImageResponse`.
- `serve/app/domain/food_nutrition.py` *(new)* — `estimate_nutrition(label) -> NutritionEstimate | None`; owns the Ollama prompt, JSON parse, and graceful-degrade. Pure domain, no HTTP/CLIP.
- `serve/app/api/v1/routes/inference.py` — wire `estimate_nutrition` into `/food-image` for top-1.
- `serve/tests/test_food_nutrition.py` *(new)* — unit tests with mocked Ollama client.
- `serve/requirements.txt` — add `pytest`.

**Spring Boot (`DACN2_BEserver`):**
- `src/main/java/.../dto/ai/AiFoodPredictResponse.java` — nested `Nutrition` + `nutrition` field.
- `src/main/java/.../dto/nutrition/NutritionAnalyzeResponse.java` — display fields on `Candidate`.
- `src/main/java/.../service/nutrition/NutritionAnalyzeService.java` — map nutrition (catalog-first / Ollama-supplement).
- `src/test/java/.../service/nutrition/NutritionAnalyzeServiceTest.java` *(new)*.

**Frontend (`DACN2_FEserver`):**
- `src/screens/CaloriesScan/CaloriesScanScreen.tsx` — accept `ingredients`.
- `src/components/CaloriesScan/ResultSheet/ResultSheet.tsx` — render ingredients block.

---

## Task 1: AI schema — `NutritionEstimate` + optional `nutrition` field

**Files:**
- Modify: `serve/app/schemas/food_image.py`

**Interfaces:**
- Consumes: nothing.
- Produces: `NutritionEstimate` (fields: `dish_name:str`, `ingredients:List[str]`, `calories:int`, `protein_g:float`, `carbs_g:float`, `fat_g:float`, `portion:str`, `disclaimer:str`); `FoodImageResponse.nutrition: Optional[NutritionEstimate] = None`.

- [ ] **Step 1: Replace the file contents**

Open `serve/app/schemas/food_image.py` and replace its entire contents with:

```python
from typing import List, Optional

from pydantic import BaseModel, Field


class FoodPrediction(BaseModel):
    label: str
    score: float


class NutritionEstimate(BaseModel):
    dish_name: str            # human-readable dish name, Vietnamese (e.g. "Phở bò")
    ingredients: List[str]    # predicted ingredients
    calories: int             # kcal for the standard portion
    protein_g: float
    carbs_g: float
    fat_g: float
    portion: str              # standard-portion description (e.g. "1 tô ~400g")
    disclaimer: str           # sentence stating this is an estimate


class FoodImageRequest(BaseModel):
    image_url: str


class FoodImageResponse(BaseModel):
    status: str
    is_food: bool
    message: str
    predictions: List[FoodPrediction] = Field(default_factory=list)
    nutrition: Optional[NutritionEstimate] = None
```

- [ ] **Step 2: Verify it imports**

Run (from repo root):
```bash
cd serve && python -c "from app.schemas.food_image import FoodImageResponse, NutritionEstimate; print(FoodImageResponse(status='success', is_food=True, message='OK').nutrition)"
```
Expected: prints `None` (the new optional field defaults to None; existing fields unchanged).

- [ ] **Step 3: Commit**

```bash
git add serve/app/schemas/food_image.py
git commit -m "feat(ai): add NutritionEstimate schema and optional nutrition field"
```

---

## Task 2: AI domain — `estimate_nutrition` with mocked-Ollama tests

**Files:**
- Create: `serve/app/domain/food_nutrition.py`
- Create: `serve/tests/test_food_nutrition.py`
- Modify: `serve/requirements.txt` (add `pytest`)

**Interfaces:**
- Consumes: `NutritionEstimate` (Task 1); `app.infra.llm_ollama.OllamaClient` (existing; `async def chat(messages: List[Dict[str,str]], temperature: float=0.2) -> str`).
- Produces: `async def estimate_nutrition(label: str, client: OllamaClient | None = None) -> Optional[NutritionEstimate]`. Returns a populated `NutritionEstimate` on valid Ollama JSON; returns `None` on any failure (Ollama down, timeout, bad/partial JSON). The optional `client` parameter exists so tests can inject a mock; production passes nothing and a default `OllamaClient()` is constructed.

- [ ] **Step 1: Add pytest to requirements**

Append a line to `serve/requirements.txt`:
```
pytest
```

Then install it:
```bash
cd serve && pip install pytest
```
Expected: pytest installs (or "already satisfied").

- [ ] **Step 2: Write the failing tests**

Create `serve/tests/test_food_nutrition.py`:

```python
import json

import pytest

from app.domain.food_nutrition import estimate_nutrition
from app.schemas.food_image import NutritionEstimate


class _FakeClient:
    """Stand-in for OllamaClient.chat — returns a canned string or raises."""

    def __init__(self, *, content=None, exc=None):
        self._content = content
        self._exc = exc

    async def chat(self, messages, temperature=0.2):
        if self._exc is not None:
            raise self._exc
        return self._content


_VALID = {
    "dish_name": "Phở bò",
    "ingredients": ["bánh phở", "thịt bò", "hành", "nước dùng"],
    "calories": 420,
    "protein_g": 25.0,
    "carbs_g": 55.0,
    "fat_g": 9.0,
    "portion": "1 tô ~400g",
    "disclaimer": "Số liệu mang tính ước lượng.",
}


@pytest.mark.asyncio
async def test_valid_json_returns_estimate():
    client = _FakeClient(content=json.dumps(_VALID))
    out = await estimate_nutrition("pho", client=client)
    assert isinstance(out, NutritionEstimate)
    assert out.dish_name == "Phở bò"
    assert out.calories == 420
    assert "thịt bò" in out.ingredients


@pytest.mark.asyncio
async def test_json_with_markdown_fences_is_parsed():
    fenced = "```json\n" + json.dumps(_VALID) + "\n```"
    client = _FakeClient(content=fenced)
    out = await estimate_nutrition("pho", client=client)
    assert isinstance(out, NutritionEstimate)
    assert out.portion == "1 tô ~400g"


@pytest.mark.asyncio
async def test_malformed_json_returns_none():
    client = _FakeClient(content="not json at all")
    out = await estimate_nutrition("pho", client=client)
    assert out is None


@pytest.mark.asyncio
async def test_missing_field_returns_none():
    partial = dict(_VALID)
    del partial["calories"]
    client = _FakeClient(content=json.dumps(partial))
    out = await estimate_nutrition("pho", client=client)
    assert out is None


@pytest.mark.asyncio
async def test_ollama_exception_returns_none():
    client = _FakeClient(exc=RuntimeError("connection refused"))
    out = await estimate_nutrition("pho", client=client)
    assert out is None
```

- [ ] **Step 3: Run the tests to verify they fail**

`pytest-asyncio` is needed for `@pytest.mark.asyncio`. Install it and add to requirements:
```bash
cd serve && pip install pytest-asyncio
```
Append `pytest-asyncio` to `serve/requirements.txt` as well.

Run:
```bash
cd serve && python -m pytest tests/test_food_nutrition.py -v -p asyncio
```
Expected: FAIL — `ModuleNotFoundError: No module named 'app.domain.food_nutrition'`.

> Note: if `@pytest.mark.asyncio` is reported as unknown, add a `serve/pytest.ini` with:
> ```ini
> [pytest]
> asyncio_mode = auto
> ```
> and re-run without `-p asyncio`.

- [ ] **Step 4: Write the implementation**

Create `serve/app/domain/food_nutrition.py`:

```python
import json
import logging
import re
from typing import Optional

from app.infra.llm_ollama import OllamaClient
from app.schemas.food_image import NutritionEstimate

logger = logging.getLogger(__name__)

_SYSTEM_PROMPT = (
    "You are a nutrition assistant. Given a dish label, estimate its "
    "nutrition for ONE STANDARD PORTION using general nutrition knowledge.\n"
    "Reply with ONLY a valid JSON object. Do NOT use markdown code fences. "
    "No text outside the JSON. The JSON must have exactly these keys:\n"
    '{\n'
    '  "dish_name": "human-readable dish name in Vietnamese",\n'
    '  "ingredients": ["main ingredients as short Vietnamese strings"],\n'
    '  "calories": <integer kcal for one standard portion>,\n'
    '  "protein_g": <number grams>,\n'
    '  "carbs_g": <number grams>,\n'
    '  "fat_g": <number grams>,\n'
    '  "portion": "standard portion description in Vietnamese, e.g. 1 tô ~400g",\n'
    '  "disclaimer": "a Vietnamese sentence stating these numbers are estimates"\n'
    '}\n'
    "All Vietnamese text. No extra keys."
)


def _extract_json_object(s: str) -> str:
    s = s.strip()
    if s.startswith("{") and s.endswith("}"):
        return s
    m = re.search(r"\{.*\}", s, flags=re.DOTALL)
    if not m:
        raise ValueError("No JSON object found in LLM output")
    return m.group(0)


async def estimate_nutrition(
    label: str, client: Optional[OllamaClient] = None
) -> Optional[NutritionEstimate]:
    """Estimate nutrition for a Food-101 label via Ollama.

    Returns a NutritionEstimate on success, or None on ANY failure
    (Ollama down/timeout, malformed or partial JSON). Never raises.
    """
    try:
        ollama = client if client is not None else OllamaClient()
        messages = [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": f"DISH_LABEL: {label}"},
        ]
        raw = await ollama.chat(messages, temperature=0.2)
        obj = json.loads(_extract_json_object(raw))
        return NutritionEstimate(**obj)
    except Exception as e:  # noqa: BLE001 - intentional graceful degrade
        logger.warning("estimate_nutrition failed for label=%s: %s", label, e)
        return None
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd serve && python -m pytest tests/test_food_nutrition.py -v
```
Expected: PASS — 5 passed.

- [ ] **Step 6: Commit**

```bash
git add serve/app/domain/food_nutrition.py serve/tests/test_food_nutrition.py serve/requirements.txt
# include serve/pytest.ini if you created it
git commit -m "feat(ai): add estimate_nutrition with graceful Ollama degrade + tests"
```

---

## Task 3: AI route — wire `estimate_nutrition` into `/food-image`

**Files:**
- Modify: `serve/app/api/v1/routes/inference.py`

**Interfaces:**
- Consumes: `estimate_nutrition` (Task 2); `FoodImageResponse.nutrition` (Task 1).
- Produces: `/food-image` now returns `nutrition` populated for top-1 food, or `null` when non-food or Ollama unavailable.

- [ ] **Step 1: Add the import**

In `serve/app/api/v1/routes/inference.py`, add to the imports near the top (after the existing `from app.schemas.food_image import ...` line, line 19):

```python
from app.domain.food_nutrition import estimate_nutrition
```

- [ ] **Step 2: Populate nutrition for top-1 in `/food-image`**

Replace the final `return` block of the `food_image` function (currently lines 53–63: the `predictions = [...]` list build and the `return FoodImageResponse(...)`) with:

```python
    # fp["food_predictions"] includes rank/label/score/source
    # Convert to the original simple format: [{label, score}, ...]
    predictions = [
        {"label": p["label"], "score": float(p["score"])}
        for p in fp["food_predictions"]
    ]

    # Estimate nutrition for the top-1 label via Ollama (graceful degrade).
    nutrition = None
    message = "OK"
    if predictions:
        nutrition = await estimate_nutrition(predictions[0]["label"])
        if nutrition is None:
            message = "Food identified, but nutrition could not be estimated."

    return FoodImageResponse(
        status="success",
        is_food=True,
        message=message,
        predictions=predictions,
        nutrition=nutrition,
    )
```

> Do NOT add `require_llm_ready` to the `food_image` decorator dependencies — the route must still succeed (with `nutrition=null`) when the LLM is unavailable.

- [ ] **Step 3: Verify the module imports**

```bash
cd serve && python -c "import app.api.v1.routes.inference as m; print('food_image' in dir(m))"
```
Expected: prints `True` (no import error).

- [ ] **Step 4: Manual integration verification (record result, do not block on Ollama)**

Start the server (`cd serve && uvicorn app.main:app --port 8000`) and in another shell:
```bash
curl -s -X POST localhost:8000/api/v1/inference/food-image \
  -H 'Content-Type: application/json' \
  -d '{"image_url":"<a real food image URL>"}' | python -m json.tool
```
Expected with Ollama running: response includes a `nutrition` object with `dish_name`, `ingredients`, `calories`. With Ollama stopped: response still HTTP 200, `nutrition` is `null`, `message` is the "could not be estimated" string. Record which you observed; if no environment is available, note that and rely on Task 2's unit tests.

- [ ] **Step 5: Commit**

```bash
git add serve/app/api/v1/routes/inference.py
git commit -m "feat(ai): return Ollama nutrition estimate for top-1 in /food-image"
```

---

## Task 4: Spring Boot DTO — `Nutrition` on `AiFoodPredictResponse`

**Files:**
- Modify: `src/main/java/com/example/dacn2_beserver/dto/ai/AiFoodPredictResponse.java`

**Interfaces:**
- Consumes: AI `/food-image` JSON `nutrition` object (Task 3).
- Produces: `AiFoodPredictResponse.getNutrition()` returning a `Nutrition` with getters `getDishName()`, `getIngredients():List<String>`, `getCalories():Integer`, `getProteinG():Double`, `getCarbsG():Double`, `getFatG():Double`, `getPortion():String`, `getDisclaimer():String`.

- [ ] **Step 1: Replace the file contents**

Replace the entire contents of `AiFoodPredictResponse.java` with:

```java
package com.example.dacn2_beserver.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFoodPredictResponse {

    private String status;

    @JsonProperty("is_food")
    private Boolean isFood;

    private String message;

    @Builder.Default
    private List<FoodPrediction> predictions = new ArrayList<>();

    private Nutrition nutrition; // nullable

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FoodPrediction {
        private String label;
        private Double score;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Nutrition {
        @JsonProperty("dish_name")
        private String dishName;
        private List<String> ingredients;
        private Integer calories;
        @JsonProperty("protein_g")
        private Double proteinG;
        @JsonProperty("carbs_g")
        private Double carbsG;
        @JsonProperty("fat_g")
        private Double fatG;
        private String portion;
        private String disclaimer;
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /d/DATN/DACN2_BEserver && ./mvnw -q -o compile
```
Expected: BUILD SUCCESS (uses the truststore workaround env from memory if needed).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/dacn2_beserver/dto/ai/AiFoodPredictResponse.java
git commit -m "feat(be): add Nutrition to AiFoodPredictResponse"
```

---

## Task 5: Spring Boot DTO — display fields on `Candidate`

**Files:**
- Modify: `src/main/java/com/example/dacn2_beserver/dto/nutrition/NutritionAnalyzeResponse.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `Candidate` gains setters/getters for `name:String`, `calories:Integer`, `protein:Integer`, `carbs:Integer`, `fat:Integer`, `serving:String`, `aiInsight:String`, `ingredients:List<String>`. The FE reads these exact JSON names (`name`, `calories`, `protein`, `carbs`, `fat`, `serving`, `aiInsight`, `ingredients`).

- [ ] **Step 1: Add the import and the new fields**

In `NutritionAnalyzeResponse.java`, add `import java.util.List;` is already present. Replace the `Candidate` static class (currently lines 32–44) with:

```java
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {
        private String code;   // AI label (matches FoodItem.code)
        private Double score;

        private CandidateStatus status; // KNOWN / UNKNOWN
        private FoodItemSnapshot foodItem; // nullable if UNKNOWN

        // Unified display fields (from catalog or Ollama)
        private String name;
        private Integer calories;
        private Integer protein;
        private Integer carbs;
        private Integer fat;
        private String serving;
        private String aiInsight;       // = Ollama disclaimer when present
        private List<String> ingredients;
    }
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /d/DATN/DACN2_BEserver && ./mvnw -q -o compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/dacn2_beserver/dto/nutrition/NutritionAnalyzeResponse.java
git commit -m "feat(be): add display fields to NutritionAnalyzeResponse.Candidate"
```

---

## Task 6: Spring Boot service — map nutrition (catalog-first / Ollama-supplement)

**Files:**
- Modify: `src/main/java/com/example/dacn2_beserver/service/nutrition/NutritionAnalyzeService.java`
- Create: `src/test/java/com/example/dacn2_beserver/service/nutrition/NutritionAnalyzeServiceTest.java`

**Interfaces:**
- Consumes: `AiFoodPredictResponse.getNutrition()` (Task 4); `Candidate` display fields (Task 5); existing `AiFoodClient`, `FoodItemRepository`.
- Produces: candidates whose top-1 carries display fields; KNOWN keeps catalog calories/macros + Ollama ingredients; UNKNOWN top-1 uses full Ollama nutrition; UNKNOWN rank 2–3 stay empty.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/example/dacn2_beserver/service/nutrition/NutritionAnalyzeServiceTest.java`:

```java
package com.example.dacn2_beserver.service.nutrition;

import com.example.dacn2_beserver.dto.ai.AiFoodPredictResponse;
import com.example.dacn2_beserver.dto.nutrition.NutritionAnalyzeResponse;
import com.example.dacn2_beserver.model.health.FoodItem;
import com.example.dacn2_beserver.repository.FoodItemRepository;
import com.example.dacn2_beserver.service.ai.AiFoodClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NutritionAnalyzeServiceTest {

    private AiFoodClient aiFoodClient;
    private FoodItemRepository foodItemRepository;
    private NutritionAnalyzeService service;

    @BeforeEach
    void setUp() {
        aiFoodClient = mock(AiFoodClient.class);
        foodItemRepository = mock(FoodItemRepository.class);
        service = new NutritionAnalyzeService(aiFoodClient, foodItemRepository);
        ReflectionTestUtils.setField(service, "confidentThreshold", 0.60);
    }

    private AiFoodPredictResponse.Nutrition pho() {
        return AiFoodPredictResponse.Nutrition.builder()
                .dishName("Phở bò")
                .ingredients(List.of("bánh phở", "thịt bò"))
                .calories(420)
                .proteinG(25.4)
                .carbsG(55.6)
                .fatG(9.2)
                .portion("1 tô ~400g")
                .disclaimer("Ước lượng.")
                .build();
    }

    @Test
    void unknownTop1_usesOllamaNutrition() {
        AiFoodPredictResponse ai = AiFoodPredictResponse.builder()
                .isFood(true).message("OK")
                .predictions(List.of(new AiFoodPredictResponse.FoodPrediction("pho", 0.9)))
                .nutrition(pho())
                .build();
        when(aiFoodClient.predictFoodByUrl("u")).thenReturn(ai);
        when(foodItemRepository.findByCode("pho")).thenReturn(Optional.empty());

        NutritionAnalyzeResponse res = service.analyzeByImageUrl("u");
        NutritionAnalyzeResponse.Candidate c = res.getCandidates().get(0);

        assertThat(c.getStatus()).isEqualTo(NutritionAnalyzeResponse.CandidateStatus.UNKNOWN);
        assertThat(c.getName()).isEqualTo("Phở bò");
        assertThat(c.getCalories()).isEqualTo(420);
        assertThat(c.getProtein()).isEqualTo(25); // rounded from 25.4
        assertThat(c.getCarbs()).isEqualTo(56);   // rounded from 55.6
        assertThat(c.getFat()).isEqualTo(9);      // rounded from 9.2
        assertThat(c.getServing()).isEqualTo("1 tô ~400g");
        assertThat(c.getIngredients()).containsExactly("bánh phở", "thịt bò");
        assertThat(c.getAiInsight()).isEqualTo("Ước lượng.");
    }

    @Test
    void knownTop1_keepsCatalogCaloriesAndTakesOllamaIngredients() {
        FoodItem item = new FoodItem();
        item.setId("id1");
        item.setCode("pho");
        item.setName("Phở (catalog)");
        item.setCalories(350);
        item.setProtein(20);
        item.setCarbs(50);
        item.setFat(8);

        AiFoodPredictResponse ai = AiFoodPredictResponse.builder()
                .isFood(true).message("OK")
                .predictions(List.of(new AiFoodPredictResponse.FoodPrediction("pho", 0.95)))
                .nutrition(pho())
                .build();
        when(aiFoodClient.predictFoodByUrl("u")).thenReturn(ai);
        when(foodItemRepository.findByCode("pho")).thenReturn(Optional.of(item));

        NutritionAnalyzeResponse res = service.analyzeByImageUrl("u");
        NutritionAnalyzeResponse.Candidate c = res.getCandidates().get(0);

        assertThat(c.getStatus()).isEqualTo(NutritionAnalyzeResponse.CandidateStatus.KNOWN);
        assertThat(c.getCalories()).isEqualTo(350);          // catalog, NOT Ollama 420
        assertThat(c.getProtein()).isEqualTo(20);            // catalog
        assertThat(c.getIngredients()).containsExactly("bánh phở", "thịt bò"); // Ollama supplement
        assertThat(c.getFoodItem()).isNotNull();
    }

    @Test
    void nullNutrition_doesNotThrow_unknownStaysEmpty() {
        AiFoodPredictResponse ai = AiFoodPredictResponse.builder()
                .isFood(true).message("OK")
                .predictions(List.of(new AiFoodPredictResponse.FoodPrediction("pho", 0.9)))
                .nutrition(null)
                .build();
        when(aiFoodClient.predictFoodByUrl("u")).thenReturn(ai);
        when(foodItemRepository.findByCode("pho")).thenReturn(Optional.empty());

        NutritionAnalyzeResponse res = service.analyzeByImageUrl("u");
        NutritionAnalyzeResponse.Candidate c = res.getCandidates().get(0);

        assertThat(c.getStatus()).isEqualTo(NutritionAnalyzeResponse.CandidateStatus.UNKNOWN);
        assertThat(c.getIngredients()).isNull();
        assertThat(c.getCalories()).isNull();
    }

    @Test
    void notFood_returnsNoCandidates() {
        AiFoodPredictResponse ai = AiFoodPredictResponse.builder()
                .isFood(false).message("not food").predictions(List.of()).build();
        when(aiFoodClient.predictFoodByUrl("u")).thenReturn(ai);

        NutritionAnalyzeResponse res = service.analyzeByImageUrl("u");

        assertThat(res.isFood()).isFalse();
        assertThat(res.getCandidates()).isEmpty();
    }
}
```

> Before running, open `src/main/java/com/example/dacn2_beserver/model/health/FoodItem.java` and confirm the setter/getter names used here (`setCalories/getCalories`, `setProtein`, `setCarbs`, `setFat`, `setCode`, `setName`, `setId`). FoodItem uses Lombok — these should exist. If a name differs, adjust the test to match the real model.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /d/DATN/DACN2_BEserver && ./mvnw -o test -Dtest=NutritionAnalyzeServiceTest
```
Expected: FAIL — assertions on `getName()/getCalories()/getIngredients()` fail because the service does not yet populate them (they are null).

- [ ] **Step 3: Implement the mapping**

In `NutritionAnalyzeService.java`, replace the candidate-building `for` loop (currently lines 47–80) with:

```java
        List<NutritionAnalyzeResponse.Candidate> candidates = new ArrayList<>();
        AiFoodPredictResponse.Nutrition nutrition = ai.getNutrition();

        for (int i = 0; i < Math.min(3, preds.size()); i++) {
            AiFoodPredictResponse.FoodPrediction p = preds.get(i);
            String code = p != null ? p.getLabel() : null;
            boolean isTop1 = (i == 0);

            NutritionAnalyzeResponse.Candidate c = NutritionAnalyzeResponse.Candidate.builder()
                    .code(code)
                    .score(p != null ? p.getScore() : null)
                    .build();

            FoodItem item = (code != null && !code.isBlank())
                    ? foodItemRepository.findByCode(code).orElse(null)
                    : null;

            if (item != null) {
                // KNOWN: catalog is the source of truth for calories/macros.
                c.setStatus(NutritionAnalyzeResponse.CandidateStatus.KNOWN);
                c.setFoodItem(NutritionAnalyzeResponse.FoodItemSnapshot.builder()
                        .id(item.getId())
                        .code(item.getCode())
                        .name(item.getName())
                        .calories(item.getCalories())
                        .carbs(item.getCarbs())
                        .fat(item.getFat())
                        .protein(item.getProtein())
                        .build());
                c.setName(item.getName());
                c.setCalories(item.getCalories());
                c.setProtein(item.getProtein());
                c.setCarbs(item.getCarbs());
                c.setFat(item.getFat());
                // Ollama supplements ingredients only (top-1 only).
                if (isTop1 && nutrition != null) {
                    c.setIngredients(nutrition.getIngredients());
                }
            } else if (isTop1 && nutrition != null) {
                // UNKNOWN top-1: full Ollama estimate.
                c.setStatus(NutritionAnalyzeResponse.CandidateStatus.UNKNOWN);
                c.setFoodItem(null);
                c.setName(nutrition.getDishName() != null ? nutrition.getDishName() : code);
                c.setCalories(nutrition.getCalories());
                c.setProtein(roundOrNull(nutrition.getProteinG()));
                c.setCarbs(roundOrNull(nutrition.getCarbsG()));
                c.setFat(roundOrNull(nutrition.getFatG()));
                c.setServing(nutrition.getPortion());
                c.setIngredients(nutrition.getIngredients());
                c.setAiInsight(nutrition.getDisclaimer());
            } else {
                // UNKNOWN rank 2-3 (or no nutrition): empty as before.
                c.setStatus(NutritionAnalyzeResponse.CandidateStatus.UNKNOWN);
                c.setFoodItem(null);
            }

            candidates.add(c);
        }
```

Add this private helper method inside the class (after `analyzeByImageUrl`):

```java
    private static Integer roundOrNull(Double v) {
        return v == null ? null : (int) Math.round(v);
    }
```

> The `primaryCandidate` / threshold / `message` block below the loop (currently lines 84–101) stays unchanged.

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /d/DATN/DACN2_BEserver && ./mvnw -o test -Dtest=NutritionAnalyzeServiceTest
```
Expected: PASS — 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/dacn2_beserver/service/nutrition/NutritionAnalyzeService.java src/test/java/com/example/dacn2_beserver/service/nutrition/NutritionAnalyzeServiceTest.java
git commit -m "feat(be): map AI nutrition into candidates (catalog-first, Ollama-supplement)"
```

---

## Task 7: FE — accept `ingredients` and render the predicted-ingredients block

**Files:**
- Modify: `src/screens/CaloriesScan/CaloriesScanScreen.tsx`
- Modify: `src/components/CaloriesScan/ResultSheet/ResultSheet.tsx`

**Interfaces:**
- Consumes: BE `Candidate.ingredients: string[]` (Task 6).
- Produces: a visible "Nguyên liệu dự đoán" block when ingredients exist; hidden when empty.

- [ ] **Step 1: Add `ingredients` to the FE DTO type**

In `src/screens/CaloriesScan/CaloriesScanScreen.tsx`, in the `NutritionCandidateDto` type (around lines 35–63), add this field (e.g. after `aiInsight?`):

```typescript
  ingredients?: string[] | null;
```

- [ ] **Step 2: Pass ingredients through `buildFoodAnalysis`**

In the same file, in the object returned by `buildFoodAnalysis` (the `return { id, name, calories, ... }` block around lines 140–173), add:

```typescript
    ingredients: candidate.ingredients ?? [],
```

- [ ] **Step 3: Add `ingredients` to the `FoodAnalysis` type**

In `src/components/CaloriesScan/ResultSheet/ResultSheet.tsx`, in the `FoodAnalysis` type (lines 15–23), add:

```typescript
  ingredients?: string[];
```

- [ ] **Step 4: Render the ingredients block**

In `ResultSheet.tsx`, inside the `result ? ( <> ... </> )` branch, insert this block between the `</View>` that closes `styles.macroRow` (after line 152) and the `insightBox` `<View>` (line 154):

```tsx
          {result.ingredients && result.ingredients.length > 0 ? (
            <View style={styles.ingredientsBox}>
              <Text style={styles.ingredientsTitle}>Nguyên liệu dự đoán</Text>
              {result.ingredients.map((ing, idx) => (
                <Text key={`${ing}-${idx}`} style={styles.ingredientItem}>
                  • {ing}
                </Text>
              ))}
            </View>
          ) : null}
```

- [ ] **Step 5: Add the styles**

In `src/components/CaloriesScan/styles.ts` (the file imported as `styles` in ResultSheet), add these entries to the `StyleSheet.create({...})` object (match the existing key style — open the file to confirm `theme` is imported; it is used elsewhere in that file):

```typescript
  ingredientsBox: {
    marginTop: 12,
    padding: 12,
    borderRadius: 12,
    backgroundColor: '#0B1221',
  },
  ingredientsTitle: {
    color: '#E2E8F0',
    fontWeight: '700',
    marginBottom: 6,
  },
  ingredientItem: {
    color: '#94A3B8',
    fontSize: 13,
    lineHeight: 20,
  },
```

> If `src/components/CaloriesScan/styles.ts` is not the correct path, find the file that defines the `styles` object imported at the top of `ResultSheet.tsx` (`import { styles } from '@components/CaloriesScan/styles';`) and add the entries there.

- [ ] **Step 6: Verify types and lint on changed files**

```bash
cd /d/DATN/DACN2_FEserver && npx tsc --noEmit
npx eslint src/screens/CaloriesScan/CaloriesScanScreen.tsx src/components/CaloriesScan/ResultSheet/ResultSheet.tsx src/components/CaloriesScan/styles.ts
```
Expected: `tsc` reports no new errors in the changed files; eslint reports no new errors.

- [ ] **Step 7: Commit (stage only the named files)**

```bash
cd /d/DATN/DACN2_FEserver
git add src/screens/CaloriesScan/CaloriesScanScreen.tsx src/components/CaloriesScan/ResultSheet/ResultSheet.tsx src/components/CaloriesScan/styles.ts
git commit -m "feat(fe): show predicted ingredients in Calories Scan result"
```

> On-device verification (user): scan a dish → see name + ingredients + calories + macros + disclaimer. Stop Ollama → still see dish name, ingredients block hidden, no crash.

---

## Self-Review

**1. Spec coverage:**
- Schema `nutrition` + `NutritionEstimate` → Task 1. ✓
- Ollama estimate + graceful degrade → Task 2. ✓
- Route wiring top-1 only, no `require_llm_ready` → Task 3. ✓
- BE `Nutrition` DTO → Task 4; `Candidate` display fields → Task 5. ✓
- Catalog-first / Ollama-supplement, UNKNOWN top-1, rank 2–3 empty, null-safe, non-food → Task 6. ✓
- FE ingredients display, hide when empty → Task 7. ✓
- Error-handling table rows all covered by Task 6 tests + Task 3 degrade. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows full code. The two "if path differs" notes (FoodItem getters, styles.ts location) are verification instructions, not placeholders — they point at real files to confirm names. ✓

**3. Type consistency:** `estimate_nutrition(label, client=None) -> Optional[NutritionEstimate]` used identically in Task 2 and Task 3. `NutritionEstimate` field names match across Task 1/2 and the BE `@JsonProperty` snake_case in Task 4. `Candidate` fields in Task 5 match the setters used in Task 6 and the FE reads in Task 7 (`name/calories/protein/carbs/fat/serving/aiInsight/ingredients`). Macro double→Integer rounding via `roundOrNull` in Task 6 matches the `Integer` types in Task 5. ✓
