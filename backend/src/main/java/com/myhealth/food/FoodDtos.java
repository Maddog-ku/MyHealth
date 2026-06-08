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
}
