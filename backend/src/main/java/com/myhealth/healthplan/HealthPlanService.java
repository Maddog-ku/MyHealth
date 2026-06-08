package com.myhealth.healthplan;

import com.myhealth.goal.GoalDtos.WeightGoalProgress;
import com.myhealth.goal.GoalDtos.SetWeightGoalRequest;
import com.myhealth.goal.GoalService;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanResponse;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanSettingsRequest;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanSettingsResponse;
import com.myhealth.healthplan.HealthPlanDtos.NutritionPlan;
import com.myhealth.healthplan.HealthPlanDtos.PlanAction;
import com.myhealth.healthplan.HealthPlanDtos.StreakPlan;
import com.myhealth.healthplan.HealthPlanDtos.WeightPlan;
import com.myhealth.healthplan.HealthPlanDtos.WorkoutPlanSummary;
import com.myhealth.stats.StatsDtos.CalorieBudgetResponse;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.stats.StatsService;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Goal;
import com.myhealth.user.UserRepository;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.workout.WorkoutGoalDtos.SetWorkoutGoalRequest;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import com.myhealth.workout.WorkoutGoalService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthPlanService {
    private final StatsService stats;
    private final GoalService weightGoals;
    private final WorkoutGoalService workoutGoals;
    private final StreakService streaks;
    private final UserRepository users;

    public HealthPlanService(StatsService stats, GoalService weightGoals,
                             WorkoutGoalService workoutGoals, StreakService streaks,
                             UserRepository users) {
        this.stats = stats;
        this.weightGoals = weightGoals;
        this.workoutGoals = workoutGoals;
        this.streaks = streaks;
        this.users = users;
    }

    public HealthPlanResponse get(AppUser user, LocalDate date) {
        DailyStatsResponse daily = stats.daily(user, date);
        CalorieBudgetResponse budget = stats.budget(user, date);
        WeightGoalProgress weight = weightGoals.get(user).progress();
        WorkoutGoalProgress workout = workoutGoals.get(user).progress();
        StreakInfo streak = streaks.overallStreak(user);

        NutritionPlan nutrition = new NutritionPlan(
                budget.goalKcal(), budget.budgetKcal(), budget.intakeKcal(), budget.burnKcal(),
                budget.remainingKcal(), budget.consumedPct(), budget.over(), budget.macros());
        WeightPlan weightPlan = toWeightPlan(daily, weight);
        WorkoutPlanSummary workoutPlan = toWorkoutPlan(daily, workout);
        StreakPlan streakPlan = new StreakPlan(streak.current(), streak.longest(), streak.lastActiveDate());
        List<PlanAction> actions = nextActions(nutrition, weightPlan, workoutPlan, streakPlan);

        return new HealthPlanResponse(date, goalLabel(user), readinessScore(nutrition, weightPlan, workoutPlan, streakPlan),
                nutrition, weightPlan, workoutPlan, streakPlan, actions);
    }

    public HealthPlanSettingsResponse settings(AppUser user) {
        DailyStatsResponse daily = stats.daily(user, LocalDate.now());
        Goal primaryGoal = user.getProfile() == null || user.getProfile().getGoal() == null
                ? Goal.maintain
                : user.getProfile().getGoal();
        return new HealthPlanSettingsResponse(
                primaryGoal,
                daily.weightKg(),
                weightGoals.get(user).progress(),
                workoutGoals.get(user).progress());
    }

    @Transactional
    public HealthPlanSettingsResponse updateSettings(AppUser user, HealthPlanSettingsRequest request) {
        if (request.weightGoal().enabled() && request.weightGoal().targetWeightKg() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                    "targetWeightKg is required when weight goal is enabled");
        }
        if (request.workoutGoal().enabled() && request.workoutGoal().targetSessionsPerWeek() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                    "targetSessionsPerWeek is required when workout goal is enabled");
        }

        user.getProfile().setGoal(request.primaryGoal());
        user.getProfile().touch();
        users.save(user);

        if (request.weightGoal().enabled()) {
            weightGoals.set(user, new SetWeightGoalRequest(
                    request.weightGoal().targetWeightKg(), request.weightGoal().targetDate()));
        } else {
            weightGoals.delete(user);
        }

        if (request.workoutGoal().enabled()) {
            workoutGoals.set(user, new SetWorkoutGoalRequest(request.workoutGoal().targetSessionsPerWeek()));
        } else {
            workoutGoals.delete(user);
        }

        return settings(user);
    }

    private WeightPlan toWeightPlan(DailyStatsResponse daily, WeightGoalProgress progress) {
        if (progress == null) {
            return new WeightPlan(false, daily.weightKg(), null, null, 0,
                    null, null, null, false);
        }
        return new WeightPlan(true, progress.currentWeightKg(), progress.targetWeightKg(), progress.remainingKg(),
                progress.progressPct(), progress.targetDate(), progress.projectedDate(),
                progress.onTrack(), progress.achieved());
    }

    private WorkoutPlanSummary toWorkoutPlan(DailyStatsResponse daily, WorkoutGoalProgress progress) {
        if (progress == null) {
            return new WorkoutPlanSummary(false, daily.workoutsDone(), daily.workoutsPlanned(),
                    null, null, null, null, false);
        }
        return new WorkoutPlanSummary(true, daily.workoutsDone(), daily.workoutsPlanned(),
                progress.targetSessionsPerWeek(), progress.completedThisWeek(),
                progress.remaining(), progress.progressPct(), progress.achieved());
    }

    private List<PlanAction> nextActions(NutritionPlan nutrition, WeightPlan weight,
                                         WorkoutPlanSummary workout, StreakPlan streak) {
        List<PlanAction> actions = new ArrayList<>();
        if (!weight.configured()) {
            actions.add(new PlanAction("SET_WEIGHT_GOAL", "設定體重目標",
                    "設定目標體重後，系統才能追蹤預估達成日與進度。", 90, "/dashboard"));
        } else if (Boolean.FALSE.equals(weight.onTrack())) {
            actions.add(new PlanAction("REVIEW_WEIGHT_GOAL", "檢查體重目標節奏",
                    "目前預估進度落後目標日期，建議重新檢查熱量與訓練安排。", 80, "/dashboard"));
        }

        if (!workout.configured()) {
            actions.add(new PlanAction("SET_WORKOUT_GOAL", "設定每週訓練目標",
                    "設定每週訓練次數後，Dashboard 會追蹤本週完成率。", 85, "/workouts"));
        } else if (!workout.achievedThisWeek() && safeInt(workout.remainingThisWeek()) > 0) {
            actions.add(new PlanAction("PLAN_WORKOUT", "安排下一次訓練",
                    "本週還差 %d 次訓練，建議先排入行事曆。".formatted(workout.remainingThisWeek()), 70, "/workouts"));
        }

        MacroBudget protein = nutrition.macros().stream()
                .filter(macro -> "protein".equals(macro.name()))
                .findFirst()
                .orElse(null);
        if (protein != null && protein.pct() < 70) {
            actions.add(new PlanAction("ADD_PROTEIN", "補足蛋白質",
                    "目前蛋白質約達成 %d%%，下一餐優先選高蛋白食物。".formatted(protein.pct()), 65, "/meals"));
        }

        if (nutrition.intakeKcal() == 0) {
            actions.add(new PlanAction("LOG_MEAL", "記錄第一餐",
                    "今天尚未記錄餐點，先用文字或照片建立今日飲食基準。", 60, "/meals"));
        } else if (nutrition.over()) {
            actions.add(new PlanAction("ADJUST_INTAKE", "調整今日熱量",
                    "今日攝取已超過預算，晚餐可改成較低熱量且高蛋白的組合。", 60, "/meals"));
        }

        if (streak.current() == 0) {
            actions.add(new PlanAction("KEEP_STREAK", "維持連續紀錄",
                    "今天完成任一餐點、訓練或體重紀錄，就能延續整體 streak。", 50, "/dashboard"));
        }

        return actions.stream()
                .sorted(Comparator.comparingInt(PlanAction::priority).reversed())
                .limit(4)
                .toList();
    }

    private int readinessScore(NutritionPlan nutrition, WeightPlan weight,
                               WorkoutPlanSummary workout, StreakPlan streak) {
        int score = 45;
        if (weight.configured()) score += 15;
        if (workout.configured()) score += 15;
        if (streak.current() > 0) score += Math.min(15, streak.current() * 3);
        if (nutrition.intakeKcal() > 0 && !nutrition.over()) score += 10;
        if (nutrition.over()) score -= 15;
        return Math.max(0, Math.min(100, score));
    }

    private String goalLabel(AppUser user) {
        Goal goal = user.getProfile() == null ? null : user.getProfile().getGoal();
        return switch (goal == null ? Goal.maintain : goal) {
            case fat_loss -> "減脂";
            case muscle_gain -> "增肌";
            case maintain -> "維持健康";
        };
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
