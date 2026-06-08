package com.myhealth.report;

import com.myhealth.ai.AiProvider;
import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.report.ReportDtos.WeeklyAdherence;
import com.myhealth.report.ReportDtos.WeeklyReportResponse;
import com.myhealth.report.ReportDtos.WeeklySummary;
import com.myhealth.report.ReportDtos.WeeklyTrend;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.stats.StatsDtos.SeriesPoint;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Goal;
import com.myhealth.user.Profile;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import com.myhealth.workout.WorkoutGoalService;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final StatsService stats;
    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;
    private final WorkoutGoalService workoutGoals;
    private final WeeklyReportRepository reports;
    private final AiProvider provider;
    private final TransactionTemplate transactionTemplate;

    public ReportService(StatsService stats, MealRepository meals, WorkoutPlanRepository workouts,
                         WorkoutGoalService workoutGoals, WeeklyReportRepository reports, AiProvider provider,
                         TransactionTemplate transactionTemplate) {
        this.stats = stats;
        this.meals = meals;
        this.workouts = workouts;
        this.workoutGoals = workoutGoals;
        this.reports = reports;
        this.provider = provider;
        this.transactionTemplate = transactionTemplate;
    }

    /** Live numbers paired with the week's goal-attainment rates. */
    record WeekData(WeeklySummary summary, WeeklyAdherence adherence) {
    }

    /** Monday of the week containing {@code date}; current week when null. */
    public LocalDate normalizeWeekStart(LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Live numbers + the cached narrative (null if none generated yet). Never calls AI. */
    public WeeklyReportResponse get(AppUser user, LocalDate weekStartInput) {
        LocalDate weekStart = normalizeWeekStart(weekStartInput);
        WeekData data = compute(user, weekStart);
        List<WeeklyTrend> trends = detectTrends(user, weekStart, data);
        WeeklyReport existing = reports.findByUserIdAndWeekStart(user.getId(), weekStart).orElse(null);
        return new WeeklyReportResponse(
                weekStart,
                weekStart.plusDays(6),
                data.summary(),
                data.adherence(),
                trends,
                existing == null ? null : existing.getContent(),
                existing == null ? null : existing.getCreatedAt());
    }

    /**
     * Generate (or regenerate) the AI narrative for the week and cache it. Deliberately
     * NOT @Transactional: the AI call can take tens of seconds and must not hold a DB
     * connection open; only the upsert below runs in a transaction.
     */
    public WeeklyReportResponse generate(AppUser user, LocalDate weekStartInput) {
        LocalDate weekStart = normalizeWeekStart(weekStartInput);
        WeekData data = compute(user, weekStart);
        WeeklySummary summary = data.summary();
        List<WeeklyTrend> trends = detectTrends(user, weekStart, data);

        String narrative = null;
        try {
            narrative = provider.weeklyReport(
                    buildReportContext(user.getProfile(), weekStart, summary, data.adherence(), trends));
        } catch (RuntimeException ex) {
            log.warn("Weekly report generation failed: {}", ex.getMessage());
        }
        boolean usedFallback = narrative == null || narrative.isBlank();
        if (usedFallback) {
            narrative = "這週的資料我先幫你整理在上方數字了 📊 本機 AI 暫時無法產生詳細回顧，"
                    + "你可以稍後再生成一次，或直接參考上面的攝取、消耗與體重變化。";
        }

        // Record which model produced it; 'fallback' marks the conservative template so a
        // stored report is never mistaken for a real model-generated narrative.
        String model = usedFallback ? "fallback" : provider.textModel();
        String finalNarrative = narrative;
        Instant generatedAt = upsertWithRetry(user, weekStart, finalNarrative, model);
        return new WeeklyReportResponse(weekStart, weekStart.plusDays(6), summary, data.adherence(),
                trends, finalNarrative, generatedAt);
    }

    private Instant upsertWithRetry(AppUser user, LocalDate weekStart, String content, String model) {
        try {
            return transactionTemplate.execute(status -> upsert(user, weekStart, content, model));
        } catch (DataIntegrityViolationException race) {
            // A concurrent generate for the same (user, week) inserted the row first and
            // tripped UNIQUE(user_id, week_start). Retry once: the row now exists, so this
            // attempt finds it and updates instead of inserting.
            log.debug("Concurrent weekly-report upsert raced on unique key; retrying as update");
            return transactionTemplate.execute(status -> upsert(user, weekStart, content, model));
        }
    }

    private Instant upsert(AppUser user, LocalDate weekStart, String content, String model) {
        WeeklyReport report = reports.findByUserIdAndWeekStart(user.getId(), weekStart).orElse(null);
        if (report == null) {
            report = new WeeklyReport(user, weekStart, content, model);
        } else {
            report.setContent(content);
            report.setModel(model);
            report.setCreatedAt(Instant.now());
        }
        return reports.save(report).getCreatedAt();
    }

    WeekData compute(AppUser user, LocalDate weekStart) {
        LocalDate today = LocalDate.now();
        LocalDate to = weekStart.plusDays(6);
        if (to.isAfter(today)) {
            to = today;
        }
        Integer workoutTarget = workoutTarget(user);
        if (to.isBefore(weekStart)) {
            // Week is entirely in the future — nothing to summarize yet.
            WeeklySummary empty = new WeeklySummary(0, 0, 0, 0, goalKcal(user, weekStart),
                    null, null, null, 0, 0, 0);
            return new WeekData(empty, new WeeklyAdherence(0, 0, 0, workoutTarget, 0, 0, 0));
        }

        RangeStatsResponse range = stats.range(user, weekStart, to);
        List<SeriesPoint> series = range.series();
        int days = series.size();
        int totalIntake = series.stream().mapToInt(SeriesPoint::intakeKcal).sum();
        int totalBurn = series.stream().mapToInt(SeriesPoint::burnKcal).sum();
        int avgIntake = days == 0 ? 0 : Math.round((float) totalIntake / days);

        BigDecimal weightStart = days == 0 ? null : series.get(0).weightKg();
        BigDecimal weightEnd = days == 0 ? null : series.get(days - 1).weightKg();
        BigDecimal weightDelta = (weightStart != null && weightEnd != null)
                ? weightEnd.subtract(weightStart) : null;

        Long userId = user.getId();
        List<Meal> weekMeals = meals.findByUserIdAndDateBetweenOrderByDateAsc(userId, weekStart, to);
        int mealsLogged = weekMeals.size();
        int workoutsDone = (int) workouts.findByUserIdAndDateBetweenOrderByDateAsc(userId, weekStart, to).stream()
                .filter(WorkoutPlan::isDone)
                .count();

        int goalKcal = goalKcal(user, to);
        WeeklySummary summary = new WeeklySummary(totalIntake, avgIntake, totalBurn, totalIntake - totalBurn,
                goalKcal, weightStart, weightEnd, weightDelta, workoutsDone, mealsLogged, days);
        WeeklyAdherence adherence = computeAdherence(user, to, series, weekMeals, days, workoutsDone, workoutTarget);
        return new WeekData(summary, adherence);
    }

    /**
     * Attainment rates over the covered week. Calorie adherence only counts days that were
     * actually logged (an un-logged day reflects missing data, not a kept budget); logging
     * adherence captures that coverage separately.
     */
    private WeeklyAdherence computeAdherence(AppUser user, LocalDate to, List<SeriesPoint> series,
                                             List<Meal> weekMeals, int days, int workoutsDone, Integer workoutTarget) {
        Set<LocalDate> loggedDays = weekMeals.stream().map(Meal::getDate).collect(Collectors.toSet());
        int daysLogged = loggedDays.size();

        int dailyGoalKcal = goalKcal(user, to);
        int daysWithinGoal = (int) series.stream()
                .filter(point -> loggedDays.contains(point.date()))
                .filter(point -> dailyGoalKcal <= 0 || point.intakeKcal() <= dailyGoalKcal)
                .count();
        int caloriePct = pct(daysWithinGoal, daysLogged);

        BigDecimal totalProtein = weekMeals.stream()
                .map(Meal::getTotalProtein)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int proteinTargetTotal = dailyProteinTargetG(user, to) * days;
        int proteinPct = pct(Math.round(totalProtein.floatValue()), proteinTargetTotal);

        int workoutPct = (workoutTarget == null || workoutTarget == 0)
                ? 0 : pct(workoutsDone, workoutTarget);
        int loggingPct = pct(daysLogged, days);

        return new WeeklyAdherence(caloriePct, proteinPct, workoutPct, workoutTarget, loggingPct, daysLogged, days);
    }

    /**
     * Surface actionable patterns for the week. Within-week signals (protein, logging) fire on
     * the current week's own numbers; cross-week signals (training volume, weight plateau) compare
     * against the previous week and only when both weeks are fully covered, so a partial current
     * week never triggers a false "decline".
     */
    private List<WeeklyTrend> detectTrends(AppUser user, LocalDate weekStart, WeekData current) {
        List<WeeklyTrend> trends = new ArrayList<>();
        WeeklySummary s = current.summary();
        WeeklyAdherence a = current.adherence();
        if (a.daysCovered() == 0) {
            return trends;  // future/empty week — nothing to detect
        }

        if (a.daysLogged() >= 2 && a.proteinPct() < 70) {
            trends.add(new WeeklyTrend("PROTEIN_LOW", "warn", "蛋白質偏低",
                    "本週蛋白質達標率約 %d%%，建議每餐安排一份高蛋白食物。".formatted(a.proteinPct())));
        }
        if (a.daysCovered() >= 4 && a.loggingPct() < 60) {
            trends.add(new WeeklyTrend("LOGGING_GAP", "info", "記錄天數不足",
                    "本週 %d 天只記錄了 %d 天，資料越完整回顧越準。".formatted(a.daysCovered(), a.daysLogged())));
        }

        WeekData prior = compute(user, weekStart.minusDays(7));
        WeeklySummary p = prior.summary();
        boolean bothWeeksComplete = a.daysCovered() == 7 && prior.adherence().daysCovered() == 7;

        if (bothWeeksComplete && p.workoutsDone() > 0 && s.workoutsDone() < p.workoutsDone()) {
            trends.add(new WeeklyTrend("WORKOUT_DECLINE", "warn", "訓練量下降",
                    "完成訓練從上週 %d 次降到本週 %d 次，留意是否需要調整課表或恢復。"
                            .formatted(p.workoutsDone(), s.workoutsDone())));
        }

        Goal goal = user.getProfile() == null ? null : user.getProfile().getGoal();
        if ((goal == Goal.fat_loss || goal == Goal.muscle_gain)
                && s.weightEnd() != null && p.weightEnd() != null
                && s.weightEnd().subtract(p.weightEnd()).abs().compareTo(new BigDecimal("0.3")) < 0) {
            trends.add(new WeeklyTrend("WEIGHT_PLATEAU", "info", "體重停滯",
                    "近兩週體重變化在 0.3 公斤內，%s進展趨緩，可重新檢視熱量或訓練安排。"
                            .formatted(goal == Goal.fat_loss ? "減脂" : "增肌")));
        }
        return trends;
    }

    /** Numerator/denominator as a 0–100 percentage; 0 when the denominator is non-positive. */
    private int pct(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round((float) numerator * 100 / denominator)));
    }

    /** The user's weekly training-session goal, or null when none is set. */
    private Integer workoutTarget(AppUser user) {
        WorkoutGoalProgress progress = workoutGoals.get(user).progress();
        return progress == null ? null : progress.targetSessionsPerWeek();
    }

    /** Daily protein target (grams) from the calorie-budget macros; 0 if unavailable. */
    private int dailyProteinTargetG(AppUser user, LocalDate date) {
        try {
            return stats.budget(user, date).macros().stream()
                    .filter(macro -> "protein".equals(macro.name()))
                    .mapToInt(MacroBudget::targetG)
                    .findFirst()
                    .orElse(0);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private int goalKcal(AppUser user, LocalDate date) {
        try {
            return stats.daily(user, date).goalKcal();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /**
     * Grounded factual block for the model: every number is supplied here, and unknowns
     * are explicitly "未提供" so the model treats them as unknown rather than inventing.
     */
    private String buildReportContext(Profile profile, LocalDate weekStart, WeeklySummary s,
                                      WeeklyAdherence a, List<WeeklyTrend> trends) {
        StringBuilder sb = new StringBuilder();
        sb.append("（以下為系統提供的真實數據，請只依據這些數字回顧，標示「未提供」者代表未知，不可臆測或編造）\n");
        sb.append("週區間: ").append(weekStart).append(" ~ ").append(weekStart.plusDays(6))
                .append("（已涵蓋 ").append(s.daysCovered()).append(" 天）\n");
        sb.append("目標: ").append(goalLabel(profile)).append("，每日熱量目標約 ").append(s.goalKcal()).append(" kcal\n");
        sb.append("本週總攝取: ").append(s.totalIntakeKcal()).append(" kcal\n");
        sb.append("平均每日攝取: ").append(s.avgIntakeKcal()).append(" kcal\n");
        sb.append("本週總消耗(運動): ").append(s.totalBurnKcal()).append(" kcal\n");
        sb.append("本週淨熱量: ").append(s.netKcal()).append(" kcal\n");
        sb.append("完成的訓練次數: ").append(s.workoutsDone()).append(" 次\n");
        sb.append("記錄的餐點筆數: ").append(s.mealsLogged()).append(" 筆\n");
        sb.append("週初體重: ").append(weightLabel(s.weightStart())).append('\n');
        sb.append("週末體重: ").append(weightLabel(s.weightEnd())).append('\n');
        sb.append("體重變化: ").append(s.weightDelta() == null ? "未提供"
                : (s.weightDelta().signum() >= 0 ? "+" : "") + plain(s.weightDelta()) + " kg").append('\n');
        sb.append("達標率（0~100%）:\n");
        sb.append("- 熱量控制達標率: ").append(a.caloriePct()).append("%（有記錄的 ")
                .append(a.daysLogged()).append(" 天中，攝取未超過每日目標的比例）\n");
        sb.append("- 蛋白質達標率: ").append(a.proteinPct()).append("%（本週實際蛋白質 ÷ 目標蛋白質）\n");
        sb.append("- 訓練次數達標率: ").append(a.workoutTarget() == null ? "未設定每週訓練目標"
                : a.workoutPct() + "%（目標每週 " + a.workoutTarget() + " 次）").append('\n');
        sb.append("- 記錄天數達標率: ").append(a.loggingPct()).append("%（").append(a.daysCovered())
                .append(" 天中有記錄餐點的天數 ").append(a.daysLogged()).append(" 天）\n");
        if (!trends.isEmpty()) {
            sb.append("系統偵測到的趨勢（請在建議中優先回應這些重點）:\n");
            for (WeeklyTrend trend : trends) {
                sb.append("- ").append(trend.title()).append(": ").append(trend.detail()).append('\n');
            }
        }
        return sb.toString();
    }

    private String weightLabel(BigDecimal value) {
        return value == null ? "未提供" : plain(value) + " kg";
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String goalLabel(Profile profile) {
        if (profile == null || profile.getGoal() == null) {
            return "維持健康";
        }
        return switch (profile.getGoal()) {
            case fat_loss -> "減脂";
            case muscle_gain -> "增肌";
            case maintain -> "維持";
        };
    }
}
