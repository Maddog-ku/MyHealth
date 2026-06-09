package com.myhealth.workout;

import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutGoalDtos.SetWorkoutGoalRequest;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutGoalService {
    private final WorkoutGoalRepository goals;
    private final WorkoutPlanRepository workouts;

    public WorkoutGoalService(WorkoutGoalRepository goals, WorkoutPlanRepository workouts) {
        this.goals = goals;
        this.workouts = workouts;
    }

    @Transactional(readOnly = true)
    public WorkoutGoalResponse get(AppUser user) {
        return goals.findByUserId(user.getId())
                .map(goal -> new WorkoutGoalResponse(progress(user, goal)))
                .orElseGet(() -> new WorkoutGoalResponse(null));
    }

    @Transactional
    public WorkoutGoalResponse set(AppUser user, SetWorkoutGoalRequest request) {
        WorkoutGoal goal = goals.findByUserId(user.getId())
                .orElseGet(() -> new WorkoutGoal(user, request.targetSessionsPerWeek()));
        goal.setTargetSessionsPerWeek(request.targetSessionsPerWeek());
        return new WorkoutGoalResponse(progress(user, goals.save(goal)));
    }

    @Transactional
    public void delete(AppUser user) {
        goals.findByUserId(user.getId()).ifPresent(goals::delete);
    }

    /** How far back to look when computing the consecutive-on-target-week streak. */
    private static final int STREAK_WINDOW_WEEKS = 52;

    private WorkoutGoalProgress progress(AppUser user, WorkoutGoal goal) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int completed = workouts.countByUserIdAndDateBetweenAndDoneTrue(user.getId(), weekStart, today);
        int target = goal.getTargetSessionsPerWeek();
        int remaining = Math.max(0, target - completed);
        int pct = target == 0 ? 0 : Math.min(100, Math.round((float) completed / target * 100));
        int streakWeeks = streakWeeks(
                workouts.findDoneWorkoutDates(user.getId(), weekStart.minusWeeks(STREAK_WINDOW_WEEKS), today),
                weekStart, target);
        return new WorkoutGoalProgress(target, completed, remaining, pct, weekStart,
                completed >= target, streakWeeks, goal.getCreatedAt());
    }

    /**
     * Consecutive weeks (Monday-anchored) that met {@code target}, ending at {@code thisWeekStart}.
     * The current week gets a grace period: if it hasn't met the target yet, the streak counts back
     * from last week instead of resetting to 0.
     */
    static int streakWeeks(List<LocalDate> doneDates, LocalDate thisWeekStart, int target) {
        if (target <= 0) {
            return 0;
        }
        Map<LocalDate, Integer> perWeek = new HashMap<>();
        for (LocalDate date : doneDates) {
            perWeek.merge(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), 1, Integer::sum);
        }
        LocalDate cursor = perWeek.getOrDefault(thisWeekStart, 0) >= target
                ? thisWeekStart : thisWeekStart.minusWeeks(1);
        int streak = 0;
        while (perWeek.getOrDefault(cursor, 0) >= target) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }
}
