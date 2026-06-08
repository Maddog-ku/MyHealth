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

    @Test
    void suggest_protein_picksMostProteinDenseFoods_whenWithinBudget() {
        var res = service.suggest(600, 30, false);

        assertThat(res.over()).isFalse();
        assertThat(res.proteinGapG()).isEqualTo(30);
        assertThat(res.headline()).contains("補蛋白質");
        assertThat(res.items()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        // All picks are protein-category foods, ordered by protein density (highest first).
        assertThat(res.items()).allSatisfy(item -> assertThat(item.category()).isEqualTo("蛋白質"));
        assertThat(res.items().get(0).protein())
                .isGreaterThanOrEqualTo(res.items().get(res.items().size() - 1).protein());
    }

    @Test
    void suggest_over_steersToLightFoods() {
        var res = service.suggest(-200, 0, true);

        assertThat(res.over()).isTrue();
        assertThat(res.remainingKcal()).isZero(); // negative remaining clamped to 0
        assertThat(res.headline()).contains("超過預算");
        assertThat(res.items()).isNotEmpty();
        // Over budget → every pick is a low-calorie serving.
        assertThat(res.items()).allSatisfy(item -> assertThat(item.kcal()).isLessThanOrEqualTo(300));
    }
}
