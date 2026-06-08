package com.myhealth.food;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FoodServiceTest {
    private final FoodService service = new FoodService();

    @Test
    void search_blankQuery_returnsEmpty() {
        assertThat(service.search("   ", null)).isEmpty();
    }

    @Test
    void search_matchesAliasesAndReturnsServingNutrition() {
        var results = service.search("chicken", null);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).name()).isEqualTo("雞胸肉");
        assertThat(results.get(0).servingGrams()).isEqualTo(150);
        assertThat(results.get(0).kcal()).isEqualTo(248);
        assertThat(results.get(0).protein()).isEqualByComparingTo("46.50");
    }

    @Test
    void search_capsLimit() {
        assertThat(service.search("a", 1)).hasSize(1);
    }
}
