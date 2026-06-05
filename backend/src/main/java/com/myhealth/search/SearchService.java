package com.myhealth.search;

import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.search.SearchDtos.SearchResponse;
import com.myhealth.search.SearchDtos.SearchResult;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Unified keyword search across the user's meals and workouts. Reads straight from
 * the existing tables (no search index); results are localized and capped here.
 */
@Service
public class SearchService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;

    public SearchService(MealRepository meals, WorkoutPlanRepository workouts) {
        this.meals = meals;
        this.workouts = workouts;
    }

    public SearchResponse search(AppUser user, String query, Integer limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new SearchResponse("", List.of());
        }
        int cap = Math.min(limit == null ? DEFAULT_LIMIT : Math.max(limit, 1), MAX_LIMIT);
        String like = "%" + q.toLowerCase() + "%";
        Pageable page = PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "date"));

        List<SearchResult> results = new ArrayList<>();
        meals.search(user.getId(), like, page).forEach(m -> results.add(mealResult(m)));
        workouts.search(user.getId(), like, page).forEach(w -> results.add(workoutResult(w)));
        results.sort(Comparator.comparing(SearchResult::date).reversed());

        return new SearchResponse(q, results.size() > cap ? List.copyOf(results.subList(0, cap)) : results);
    }

    private SearchResult mealResult(Meal m) {
        String slot = slotLabel(m.getSlot());
        String desc = m.getDescription();
        String title = (desc != null && !desc.isBlank()) ? desc.strip() : slot;
        return new SearchResult("MEAL", m.getId(), title, slot, m.getDate(), m.getTotalKcal());
    }

    private SearchResult workoutResult(WorkoutPlan w) {
        return new SearchResult("WORKOUT", w.getId(), categoryLabel(w.getCategory()),
                w.isDone() ? "已完成" : "未完成", w.getDate(), w.getTotalKcal());
    }

    private String slotLabel(String slot) {
        if (slot == null) {
            return "餐點";
        }
        return switch (slot) {
            case "breakfast" -> "早餐";
            case "lunch" -> "午餐";
            case "dinner" -> "晚餐";
            case "snack" -> "點心";
            default -> slot;
        };
    }

    private String categoryLabel(String category) {
        if (category == null) {
            return "訓練";
        }
        return switch (category) {
            case "abs" -> "腹肌核心";
            case "waist" -> "腰腹側線";
            case "legs" -> "腿部肌群";
            case "chest" -> "胸部塑造";
            case "back" -> "背部強化";
            case "arms" -> "手臂雕塑";
            case "glutes" -> "臀部緊實";
            case "cardio" -> "高效有氧";
            case "full_body" -> "全身燃脂";
            default -> category;
        };
    }
}
