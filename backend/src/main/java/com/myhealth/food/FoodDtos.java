package com.myhealth.food;

import java.math.BigDecimal;
import java.util.List;

public final class FoodDtos {
    private FoodDtos() {
    }

    public record FoodResponse(
            String id,
            String name,
            String category,
            int servingGrams,
            int kcal,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carb,
            List<String> aliases
    ) {
    }

    /** One recommended food (a common serving) to help fill today's nutrition gap. */
    public record FoodSuggestion(
            String id,
            String name,
            String category,
            int grams,
            int kcal,
            BigDecimal protein,
            String reason
    ) {
    }

    /**
     * Concrete next-meal food picks grounded in the catalog, chosen from today's
     * remaining budget and protein gap.
     *
     * @param remainingKcal calories left in today's budget (clamped at 0)
     * @param proteinGapG   grams of protein still needed to hit today's target (clamped at 0)
     * @param over          true when today's intake already exceeds the budget
     */
    public record FoodSuggestionsResponse(
            int remainingKcal,
            int proteinGapG,
            boolean over,
            String headline,
            List<FoodSuggestion> items
    ) {
    }
}
