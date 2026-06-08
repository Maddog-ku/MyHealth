package com.myhealth.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.auth.AuthMapper;
import com.myhealth.export.ExportDtos.AchievementExport;
import com.myhealth.export.ExportDtos.ExportFile;
import com.myhealth.export.ExportDtos.FavoriteMealExport;
import com.myhealth.export.ExportDtos.HabitExport;
import com.myhealth.export.ExportDtos.MealExport;
import com.myhealth.export.ExportDtos.MeasurementExport;
import com.myhealth.export.ExportDtos.WeightGoalExport;
import com.myhealth.export.ExportDtos.WorkoutExport;
import com.myhealth.export.ExportDtos.WorkoutGoalExport;
import com.myhealth.goal.WeightGoal;
import com.myhealth.goal.WeightGoalRepository;
import com.myhealth.habit.HabitLogRepository;
import com.myhealth.meal.FavoriteMealRepository;
import com.myhealth.meal.MealRepository;
import com.myhealth.streak.AchievementCatalog;
import com.myhealth.streak.AchievementRepository;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.workout.WorkoutGoalRepository;
import com.myhealth.workout.WorkoutPlanRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a complete, downloadable JSON snapshot of a user's data. Read-only and assembled
 * directly from the repositories so it stays in sync with what {@code DELETE /me} would erase.
 */
@Service
public class DataExportService {
    private final BodyMeasurementRepository measurements;
    private final WorkoutPlanRepository workouts;
    private final MealRepository meals;
    private final WeightGoalRepository weightGoals;
    private final FavoriteMealRepository favoriteMeals;
    private final HabitLogRepository habits;
    private final AchievementRepository achievements;
    private final WorkoutGoalRepository workoutGoals;
    private final ObjectMapper objectMapper;

    public DataExportService(BodyMeasurementRepository measurements, WorkoutPlanRepository workouts,
                             MealRepository meals, WeightGoalRepository weightGoals,
                             FavoriteMealRepository favoriteMeals, HabitLogRepository habits,
                             AchievementRepository achievements, WorkoutGoalRepository workoutGoals,
                             ObjectMapper objectMapper) {
        this.measurements = measurements;
        this.workouts = workouts;
        this.meals = meals;
        this.weightGoals = weightGoals;
        this.favoriteMeals = favoriteMeals;
        this.habits = habits;
        this.achievements = achievements;
        this.workoutGoals = workoutGoals;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ExportFile export(AppUser user) {
        Long userId = user.getId();

        List<MeasurementExport> measurementExports = measurements.findByUserIdOrderByMeasuredAtAsc(userId).stream()
                .map(m -> new MeasurementExport(m.getMeasuredAt(), m.getWeightKg(), m.getBodyFatPct(),
                        m.getMuscleMassKg(), m.getBmrKcal(), m.getWaistCm(), m.getBodyWaterPct(), m.getNote()))
                .toList();

        List<WorkoutExport> workoutExports = workouts.findByUserIdOrderByDateAscCreatedAtAsc(userId).stream()
                .map(w -> new WorkoutExport(w.getDate(), w.getCategory(), parseJson(w.getItemsJson()),
                        w.getTotalKcal(), w.getBurnedKcal(), w.isDone(), w.getCreatedAt()))
                .toList();

        List<MealExport> mealExports = meals.findByUserIdOrderByDateAscCreatedAtAsc(userId).stream()
                .map(m -> new MealExport(m.getDate(), m.getSlot(), m.getDescription(), m.getImageUrl(),
                        parseJson(m.getItemsJson()), m.getTotalKcal(), m.getTotalProtein(), m.getTotalFat(),
                        m.getTotalCarb(), m.getAiSuggestion(), m.getCreatedAt()))
                .toList();

        WeightGoalExport goalExport = weightGoals.findByUserId(userId)
                .map(this::toGoalExport)
                .orElse(null);

        List<FavoriteMealExport> favoriteExports = favoriteMeals.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> new FavoriteMealExport(f.getName(), f.getSlot(), f.getDescription(),
                        parseJson(f.getItemsJson()), f.getTotalKcal(), f.getTotalProtein(), f.getTotalFat(),
                        f.getTotalCarb(), f.getAiSuggestion(), f.getCreatedAt()))
                .toList();

        List<HabitExport> habitExports = habits.findByUserIdOrderByDateAscTypeAsc(userId).stream()
                .map(h -> new HabitExport(h.getDate(), h.getType().name(), h.getCompletedAt()))
                .toList();

        List<AchievementExport> achievementExports = achievements.findByUserId(userId).stream()
                .map(a -> new AchievementExport(a.getCode(), badgeTitle(a.getCode()), a.getUnlockedAt()))
                .toList();

        WorkoutGoalExport workoutGoalExport = workoutGoals.findByUserId(userId)
                .map(g -> new WorkoutGoalExport(g.getTargetSessionsPerWeek(), g.getCreatedAt()))
                .orElse(null);

        return new ExportFile(
                Instant.now().toString(),
                AuthMapper.toUserResponse(user),
                measurementExports,
                workoutExports,
                mealExports,
                goalExport,
                favoriteExports,
                habitExports,
                achievementExports,
                workoutGoalExport);
    }

    /** Human-readable badge title from the catalog, or null for a retired code. */
    private String badgeTitle(String code) {
        try {
            return AchievementCatalog.valueOf(code).title();
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private WeightGoalExport toGoalExport(WeightGoal g) {
        return new WeightGoalExport(g.getTargetWeightKg(), g.getStartWeightKg(),
                g.getStartDate(), g.getTargetDate(), g.getCreatedAt());
    }

    /** Embed a stored JSONB column as real nested JSON; never throw, fall back to an empty array. */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createArrayNode();
        }
    }
}
