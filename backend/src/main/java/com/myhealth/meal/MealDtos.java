package com.myhealth.meal;

import com.myhealth.ai.AiProvider.FoodItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MealDtos {
    private MealDtos() {
    }

    public record MealResponse(
            Long id,
            LocalDate date,
            String slot,
            String description,
            String imageUrl,
            List<FoodItem> items,
            int totalKcal,
            BigDecimal totalProtein,
            BigDecimal totalFat,
            BigDecimal totalCarb,
            String aiSuggestion,
            Instant createdAt
    ) {
    }

    public record UpdateMealRequest(List<FoodItem> items, String aiSuggestion) {
    }
}
