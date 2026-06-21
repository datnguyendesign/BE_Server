package com.example.dacn2_beserver.service.nutrition;

import com.example.dacn2_beserver.dto.ai.AiFoodPredictResponse;
import com.example.dacn2_beserver.dto.nutrition.NutritionAnalyzeResponse;
import com.example.dacn2_beserver.model.health.FoodItem;
import com.example.dacn2_beserver.repository.FoodItemRepository;
import com.example.dacn2_beserver.service.ai.AiFoodClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NutritionAnalyzeService {

    private final AiFoodClient aiFoodClient;
    private final FoodItemRepository foodItemRepository;

    @Value("${nutrition.confident-threshold:0.60}")
    private double confidentThreshold;

    public NutritionAnalyzeResponse analyzeByImageUrl(String imageUrl) {
        AiFoodPredictResponse ai = aiFoodClient.predictFoodByUrl(imageUrl);

        boolean isFood = Boolean.TRUE.equals(ai.getIsFood());

        NutritionAnalyzeResponse res = NutritionAnalyzeResponse.builder()
                .isFood(isFood)
                .message(ai.getMessage())
                .thresholdUsed(confidentThreshold)
                .candidates(new ArrayList<>())
                .build();

        if (!isFood) {
            // Non-food: return AI message, no candidates
            res.setCandidates(List.of());
            res.setPrimaryCandidate(null);
            return res;
        }

        List<AiFoodPredictResponse.FoodPrediction> preds =
                ai.getPredictions() != null ? ai.getPredictions() : List.of();

        List<NutritionAnalyzeResponse.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < Math.min(3, preds.size()); i++) {
            AiFoodPredictResponse.FoodPrediction p = preds.get(i);
            String code = p != null ? p.getLabel() : null;

            NutritionAnalyzeResponse.Candidate c = NutritionAnalyzeResponse.Candidate.builder()
                    .code(code)
                    .score(p != null ? p.getScore() : null)
                    .build();

            if (code != null && !code.isBlank()) {
                FoodItem item = foodItemRepository.findByCode(code).orElse(null);
                if (item != null) {
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
                } else {
                    c.setStatus(NutritionAnalyzeResponse.CandidateStatus.UNKNOWN);
                    c.setFoodItem(null);
                }
            } else {
                c.setStatus(NutritionAnalyzeResponse.CandidateStatus.UNKNOWN);
                c.setFoodItem(null);
            }

            candidates.add(c);
        }

        res.setCandidates(candidates);

        // Determine confident primary candidate
        NutritionAnalyzeResponse.Candidate top1 = candidates.isEmpty() ? null : candidates.get(0);
        Double top1Score = top1 != null ? top1.getScore() : null;

        if (top1 != null && top1Score != null && top1Score >= confidentThreshold) {
            res.setPrimaryCandidate(top1);

            String name = (top1.getFoodItem() != null && top1.getFoodItem().getName() != null && !top1.getFoodItem().getName().isBlank())
                    ? top1.getFoodItem().getName()
                    : top1.getCode();

            res.setMessage("We detected: " + name + ". Confirm to log?");
        } else {
            res.setPrimaryCandidate(null);
            res.setMessage("Please choose the correct food.");
        }

        return res;
    }
}