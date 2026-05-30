package com.myhealth.meal;

import com.myhealth.ai.AiProvider.FoodItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    public record UpdateMealRequest(
            @NotNull @Size(max = 5) List<@Valid FoodItem> items,
            @Size(max = 120) @Pattern(regexp = "^[\\p{L}\\p{N}\\s，。,.!?、:：()（）_-]*$") String aiSuggestion
    ) {
    }
}
