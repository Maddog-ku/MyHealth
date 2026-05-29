package com.myhealth.stats;

import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.stats.StatsDtos.SeriesPoint;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StatsService {
    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;

    public StatsService(MealRepository meals, WorkoutPlanRepository workouts) {
        this.meals = meals;
        this.workouts = workouts;
    }

    public DailyStatsResponse daily(AppUser user, LocalDate date) {
        List<Meal> dayMeals = meals.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), date);
        List<WorkoutPlan> dayWorkouts = workouts.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), date);

        int intake = dayMeals.stream().mapToInt(Meal::getTotalKcal).sum();
        BigDecimal protein = sum(dayMeals.stream().map(Meal::getTotalProtein).toList());
        BigDecimal fat = sum(dayMeals.stream().map(Meal::getTotalFat).toList());
        BigDecimal carb = sum(dayMeals.stream().map(Meal::getTotalCarb).toList());
        int burn = dayWorkouts.stream().filter(WorkoutPlan::isDone).mapToInt(WorkoutPlan::getTotalKcal).sum();
        int done = (int) dayWorkouts.stream().filter(WorkoutPlan::isDone).count();

        return new DailyStatsResponse(
                date,
                intake,
                burn,
                intake - burn,
                protein,
                fat,
                carb,
                user.getProfile().getWeightKg(),
                1700,
                done,
                dayWorkouts.size());
    }

    public RangeStatsResponse range(AppUser user, LocalDate from, LocalDate to) {
        if (ChronoUnit.DAYS.between(from, to) > 90) {
            to = from.plusDays(90);
        }
        List<Meal> rangeMeals = meals.findByUserIdAndDateBetweenOrderByDateAsc(user.getId(), from, to);
        List<WorkoutPlan> rangeWorkouts = workouts.findByUserIdAndDateBetweenOrderByDateAsc(user.getId(), from, to);
        List<SeriesPoint> series = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate current = date;
            int intake = rangeMeals.stream()
                    .filter(meal -> meal.getDate().equals(current))
                    .mapToInt(Meal::getTotalKcal)
                    .sum();
            int burn = rangeWorkouts.stream()
                    .filter(workout -> workout.getDate().equals(current) && workout.isDone())
                    .mapToInt(WorkoutPlan::getTotalKcal)
                    .sum();
            series.add(new SeriesPoint(current, intake, burn, user.getProfile().getWeightKg()));
        }
        return new RangeStatsResponse(from, to, series);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
