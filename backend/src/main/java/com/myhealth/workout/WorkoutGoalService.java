package com.myhealth.workout;

import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutGoalDtos.SetWorkoutGoalRequest;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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

    private WorkoutGoalProgress progress(AppUser user, WorkoutGoal goal) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int completed = workouts.countByUserIdAndDateBetweenAndDoneTrue(user.getId(), weekStart, today);
        int target = goal.getTargetSessionsPerWeek();
        int remaining = Math.max(0, target - completed);
        int pct = target == 0 ? 0 : Math.min(100, Math.round((float) completed / target * 100));
        return new WorkoutGoalProgress(target, completed, remaining, pct, weekStart,
                completed >= target, goal.getCreatedAt());
    }
}
