package com.myhealth.healthplan;

import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.goal.GoalDtos.WeightGoalProgress;
import com.myhealth.user.Goal;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class HealthPlanDtos {
    private HealthPlanDtos() {
    }

    public record HealthPlanResponse(
            LocalDate date,
            String primaryGoal,
            int readinessScore,
            NutritionPlan nutrition,
            WeightPlan weight,
            WorkoutPlanSummary workout,
            StreakPlan streak,
            List<PlanAction> nextActions
    ) {
    }

    public record NutritionPlan(
            int goalKcal,
            int budgetKcal,
            int intakeKcal,
            int burnKcal,
            int remainingKcal,
            int consumedPct,
            boolean over,
            List<MacroBudget> macros
    ) {
    }

    public record WeightPlan(
            boolean configured,
            BigDecimal currentWeightKg,
            BigDecimal targetWeightKg,
            BigDecimal remainingKg,
            int progressPct,
            LocalDate targetDate,
            LocalDate projectedDate,
            Boolean onTrack,
            boolean achieved
    ) {
    }

    public record WorkoutPlanSummary(
            boolean configured,
            int workoutsDoneToday,
            int workoutsPlannedToday,
            Integer targetSessionsPerWeek,
            Integer completedThisWeek,
            Integer remainingThisWeek,
            Integer progressPct,
            boolean achievedThisWeek
    ) {
    }

    public record StreakPlan(
            int current,
            int longest,
            LocalDate lastActiveDate
    ) {
    }

    public record PlanAction(
            String type,
            String title,
            String detail,
            int priority,
            String href
    ) {
    }

    public record HealthPlanSettingsResponse(
            Goal primaryGoal,
            BigDecimal currentWeightKg,
            WeightGoalProgress weightGoal,
            WorkoutGoalProgress workoutGoal
    ) {
    }

    public record HealthPlanSettingsRequest(
            @NotNull Goal primaryGoal,
            @NotNull @Valid WeightGoalSettings weightGoal,
            @NotNull @Valid WorkoutGoalSettings workoutGoal
    ) {
    }

    public record WeightGoalSettings(
            boolean enabled,
            @DecimalMin("20.0") @DecimalMax("400.0") @Digits(integer = 3, fraction = 1) BigDecimal targetWeightKg,
            LocalDate targetDate
    ) {
    }

    public record WorkoutGoalSettings(
            boolean enabled,
            @Min(1) @Max(14) Integer targetSessionsPerWeek
    ) {
    }
}
