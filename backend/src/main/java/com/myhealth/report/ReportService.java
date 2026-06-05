package com.myhealth.report;

import com.myhealth.ai.AiProvider;
import com.myhealth.meal.MealRepository;
import com.myhealth.report.ReportDtos.WeeklyReportResponse;
import com.myhealth.report.ReportDtos.WeeklySummary;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.stats.StatsDtos.SeriesPoint;
import com.myhealth.stats.StatsService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Profile;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
    private final WeeklyReportRepository reports;
    private final AiProvider provider;
    private final TransactionTemplate transactionTemplate;

    public ReportService(StatsService stats, MealRepository meals, WorkoutPlanRepository workouts,
                         WeeklyReportRepository reports, AiProvider provider,
                         TransactionTemplate transactionTemplate) {
        this.stats = stats;
        this.meals = meals;
        this.workouts = workouts;
        this.reports = reports;
        this.provider = provider;
        this.transactionTemplate = transactionTemplate;
    }

    /** Monday of the week containing {@code date}; current week when null. */
    public LocalDate normalizeWeekStart(LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Live numbers + the cached narrative (null if none generated yet). Never calls AI. */
    public WeeklyReportResponse get(AppUser user, LocalDate weekStartInput) {
        LocalDate weekStart = normalizeWeekStart(weekStartInput);
        WeeklySummary summary = computeSummary(user, weekStart);
        WeeklyReport existing = reports.findByUserIdAndWeekStart(user.getId(), weekStart).orElse(null);
        return new WeeklyReportResponse(
                weekStart,
                weekStart.plusDays(6),
                summary,
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
        WeeklySummary summary = computeSummary(user, weekStart);

        String narrative = null;
        try {
            narrative = provider.weeklyReport(buildReportContext(user.getProfile(), weekStart, summary));
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
        return new WeeklyReportResponse(weekStart, weekStart.plusDays(6), summary, finalNarrative, generatedAt);
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

    WeeklySummary computeSummary(AppUser user, LocalDate weekStart) {
        LocalDate today = LocalDate.now();
        LocalDate to = weekStart.plusDays(6);
        if (to.isAfter(today)) {
            to = today;
        }
        if (to.isBefore(weekStart)) {
            // Week is entirely in the future — nothing to summarize yet.
            return new WeeklySummary(0, 0, 0, 0, goalKcal(user, weekStart),
                    null, null, null, 0, 0, 0);
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
        int mealsLogged = meals.findByUserIdAndDateBetweenOrderByDateAsc(userId, weekStart, to).size();
        int workoutsDone = (int) workouts.findByUserIdAndDateBetweenOrderByDateAsc(userId, weekStart, to).stream()
                .filter(WorkoutPlan::isDone)
                .count();

        return new WeeklySummary(totalIntake, avgIntake, totalBurn, totalIntake - totalBurn,
                goalKcal(user, to), weightStart, weightEnd, weightDelta, workoutsDone, mealsLogged, days);
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
    private String buildReportContext(Profile profile, LocalDate weekStart, WeeklySummary s) {
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
