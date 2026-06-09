package com.myhealth.streak;

import com.myhealth.habit.HabitLog;
import com.myhealth.habit.HabitLogRepository;
import com.myhealth.habit.HabitType;
import com.myhealth.meal.MealRepository;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakDtos.StreakSummaryResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.workout.WorkoutGoal;
import com.myhealth.workout.WorkoutGoalRepository;
import com.myhealth.workout.WorkoutPlanRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Streaks are computed live from the user's meals/workouts/measurements — never
 * stored — so they can never drift from the underlying data. Only the achievement
 * badges they unlock are persisted (see {@link AchievementService}).
 */
@Service
public class StreakService {
    /** How far back to look; comfortably covers the longest badge threshold (30 days). */
    private static final int WINDOW_DAYS = 365;
    /** Shorter window for the read-only current-streak lookup used by reminders. */
    private static final int REMINDER_WINDOW_DAYS = 60;

    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;
    private final WorkoutGoalRepository workoutGoals;
    private final BodyMeasurementRepository bodyMeasurements;
    private final HabitLogRepository habitLogs;
    private final AchievementService achievements;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public StreakService(MealRepository meals, WorkoutPlanRepository workouts,
                         WorkoutGoalRepository workoutGoals, BodyMeasurementRepository bodyMeasurements,
                         HabitLogRepository habitLogs, AchievementService achievements) {
        this.meals = meals;
        this.workouts = workouts;
        this.workoutGoals = workoutGoals;
        this.bodyMeasurements = bodyMeasurements;
        this.habitLogs = habitLogs;
        this.achievements = achievements;
    }

    public StreakSummaryResponse getSummary(AppUser user) {
        Long userId = user.getId();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate from = today.minusDays(WINDOW_DAYS - 1);

        // Project only the date/timestamp columns (filtering + distinct done in SQL) rather
        // than hydrating a year of full Meal/Workout/Measurement rows just to read one field.
        Set<LocalDate> mealDays = new TreeSet<>(meals.findDistinctMealDates(userId, from, today));
        Set<LocalDate> workoutDays = new TreeSet<>(workouts.findDistinctDoneWorkoutDates(userId, from, today));

        Set<LocalDate> weightDays = new TreeSet<>();
        bodyMeasurements.findMeasuredAtBetween(userId, startOfDay(from), endOfDay(today))
                .forEach(at -> weightDays.add(at.atZone(zoneId).toLocalDate()));

        Set<LocalDate> overallDays = new TreeSet<>(mealDays);
        overallDays.addAll(workoutDays);
        overallDays.addAll(weightDays);

        StreakInfo mealStreak = streakOf(mealDays, today);
        StreakInfo workoutStreak = streakOf(workoutDays, today);
        StreakInfo overallStreak = streakOf(overallDays, today);

        int weeklyTarget = workoutGoals.findByUserId(userId)
                .map(WorkoutGoal::getTargetSessionsPerWeek)
                .orElse(0);
        int weeklyGoalHits = weeklyGoalHits(workouts.findDoneWorkoutDates(userId, from, today), weeklyTarget);
        int longestHabitStreak = longestHabitStreak(habitLogs.findByUserIdAndDateBetween(userId, from, today), today);

        StreakMetrics metrics = new StreakMetrics(
                overallStreak.longest(),
                meals.countByUserId(userId),
                workouts.countByUserIdAndDoneTrue(userId),
                bodyMeasurements.countByUserId(userId),
                meals.countPhotoMeals(userId),
                weeklyGoalHits,
                longestHabitStreak);

        AchievementService.ReconcileResult rc = achievements.reconcile(user, metrics);
        return new StreakSummaryResponse(mealStreak, workoutStreak, overallStreak, rc.views(), rc.newlyUnlocked());
    }

    /**
     * Read-only overall streak (any activity counts) over a short window — does NOT
     * reconcile achievements. Used by the notification center to decide reminders.
     */
    public StreakInfo overallStreak(AppUser user) {
        Long userId = user.getId();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate from = today.minusDays(REMINDER_WINDOW_DAYS - 1);

        Set<LocalDate> days = new TreeSet<>(meals.findDistinctMealDates(userId, from, today));
        days.addAll(workouts.findDistinctDoneWorkoutDates(userId, from, today));
        bodyMeasurements.findMeasuredAtBetween(userId, startOfDay(from), endOfDay(today))
                .forEach(at -> days.add(at.atZone(zoneId).toLocalDate()));
        return streakOf(days, today);
    }

    /**
     * Current/longest consecutive-day run in {@code active}. The current streak gets a
     * grace day for today: if today has no activity yet it counts back from yesterday,
     * so an unlogged morning doesn't prematurely show the streak as broken.
     */
    static StreakInfo streakOf(Set<LocalDate> active, LocalDate today) {
        if (active.isEmpty()) {
            return new StreakInfo(0, 0, null);
        }
        LocalDate cursor = active.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (active.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }

        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate day : active) { // TreeSet iterates ascending
            run = (prev != null && day.equals(prev.plusDays(1))) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = day;
        }
        return new StreakInfo(current, longest, prev); // prev is the last (max) day
    }

    /**
     * How many distinct weeks (Monday-anchored) had at least {@code target} completed workouts.
     * Returns 0 when no weekly goal is set, so the goal-based badges stay locked until the
     * user opts into a target.
     */
    static int weeklyGoalHits(List<LocalDate> doneDates, int target) {
        if (target <= 0) {
            return 0;
        }
        Map<LocalDate, Integer> sessionsPerWeek = new HashMap<>();
        for (LocalDate date : doneDates) {
            LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            sessionsPerWeek.merge(monday, 1, Integer::sum);
        }
        return (int) sessionsPerWeek.values().stream().filter(count -> count >= target).count();
    }

    /**
     * Longest consecutive-day completion run for any single habit within the window — the
     * basis for habit-consistency badges. 0 when no habits were ever completed.
     */
    static int longestHabitStreak(List<HabitLog> logs, LocalDate today) {
        Map<HabitType, Set<LocalDate>> byType = new EnumMap<>(HabitType.class);
        for (HabitLog log : logs) {
            byType.computeIfAbsent(log.getType(), type -> new TreeSet<>()).add(log.getDate());
        }
        return byType.values().stream()
                .mapToInt(days -> streakOf(days, today).longest())
                .max()
                .orElse(0);
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
    }
}
