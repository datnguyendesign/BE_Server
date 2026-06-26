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
