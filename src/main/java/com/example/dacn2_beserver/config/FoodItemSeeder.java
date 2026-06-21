package com.example.dacn2_beserver.config;

import com.example.dacn2_beserver.model.health.FoodItem;
import com.example.dacn2_beserver.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class FoodItemSeeder implements CommandLineRunner {

    private final FoodItemRepository foodItemRepository;

    @Override
    public void run(String... args) {
        if (foodItemRepository.count() > 0) {
            return; // đã seed rồi thì bỏ qua
        }

        List<FoodItem> items = List.of(
                food("apple_pie", "Apple Pie", 237, 34, 11, 2),
                food("baby_back_ribs", "Baby Back Ribs", 292, 0, 21, 23),
                food("baklava", "Baklava", 334, 41, 18, 5),
                food("beef_carpaccio", "Beef Carpaccio", 210, 2, 12, 22),
                food("beef_tartare", "Beef Tartare", 250, 1, 18, 20),
                food("beet_salad", "Beet Salad", 120, 10, 7, 4),
                food("bibimbap", "Bibimbap", 560, 78, 17, 20),
                food("caesar_salad", "Caesar Salad", 180, 9, 14, 7),
                food("cheesecake", "Cheesecake", 321, 25, 23, 6),
                food("chicken_curry", "Chicken Curry", 320, 12, 18, 25),
                food("french_fries", "French Fries", 312, 41, 15, 3),
                food("hamburger", "Hamburger", 295, 30, 14, 17),
                food("ice_cream", "Ice Cream", 207, 24, 11, 4),
                food("pizza", "Pizza", 266, 33, 10, 11),
                food("pho", "Pho", 350, 45, 6, 25),
                food("sushi", "Sushi", 200, 28, 3, 9),
                food("tacos", "Tacos", 226, 20, 12, 9)
                // 👉 có thể thêm dần 101 món, không bắt buộc làm hết ngay
        );

        foodItemRepository.saveAll(items);
        System.out.println("[FoodItemSeeder] Seeded " + items.size() + " food items");
    }

    private FoodItem food(
            String code,
            String name,
            int kcal,
            int carbs,
            int fat,
            int protein
    ) {
        return FoodItem.builder()
                .code(code)
                .name(name)
                .calories(kcal)
                .carbs(carbs)
                .fat(fat)
                .protein(protein)
                .build();
    }
}