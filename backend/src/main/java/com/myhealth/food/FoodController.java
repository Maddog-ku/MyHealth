package com.myhealth.food;

import com.myhealth.auth.CurrentUser;
import com.myhealth.food.FoodDtos.FoodResponse;
import com.myhealth.food.FoodDtos.FoodSuggestionsResponse;
import com.myhealth.stats.StatsDtos.CalorieBudgetResponse;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foods")
public class FoodController {
    private final FoodService foodService;
    private final StatsService stats;
    private final CurrentUser currentUser;

    public FoodController(FoodService foodService, StatsService stats, CurrentUser currentUser) {
        this.foodService = foodService;
        this.stats = stats;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<FoodResponse> search(@RequestParam(name = "q", required = false) String q,
                              @RequestParam(required = false) Integer limit) {
        return foodService.search(q, limit);
    }

    /** Concrete next-meal food picks from the catalog, grounded in today's remaining budget and protein gap. */
    @GetMapping("/suggestions")
    FoodSuggestionsResponse suggestions() {
        AppUser user = currentUser.require();
        CalorieBudgetResponse budget = stats.budget(user, LocalDate.now());
        int proteinGap = budget.macros().stream()
                .filter(macro -> "protein".equals(macro.name()))
                .map(macro -> Math.max(0, macro.targetG() - macro.consumedG()))
                .findFirst()
                .orElse(0);
        return foodService.suggest(budget.remainingKcal(), proteinGap, budget.over());
    }
}
