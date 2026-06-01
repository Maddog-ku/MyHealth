package com.myhealth.stats;

import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.stats.StatsDtos.SeriesPoint;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Goal;
import com.myhealth.user.Profile;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StatsService {
    private final MealRepository meals;
    private final WorkoutPlanRepository workouts;
    private final BodyMeasurementRepository bodyMeasurements;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public StatsService(MealRepository meals, WorkoutPlanRepository workouts, BodyMeasurementRepository bodyMeasurements) {
        this.meals = meals;
        this.workouts = workouts;
        this.bodyMeasurements = bodyMeasurements;
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
                latestWeightOnOrBefore(user, date),
                targetKcal(user.getProfile()),
                done,
                dayWorkouts.size());
    }

    public RangeStatsResponse range(AppUser user, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "from must be on or before to");
        }
        if (days > 90) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "date range must be 90 days or less");
        }
        Long userId = user.getId();
        List<Meal> rangeMeals = meals.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to);
        List<WorkoutPlan> rangeWorkouts = workouts.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to);
        List<BodyMeasurement> measurements = measurementsForRange(userId, from, to);
        List<SeriesPoint> series = new ArrayList<>();
        // Baseline: the most recent measurement on/before the day before `from`
        // holds a full snapshot (UserService writes all metrics on every update),
        // so each metric carries forward from there (falling back to the profile).
        Profile profile = user.getProfile();
        BodyMeasurement baseline =
                bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, endOfDay(from.minusDays(1)))
                        .orElse(null);
        BigDecimal currentWeight = nonNull(baseline == null ? null : baseline.getWeightKg(), profile.getWeightKg());
        BigDecimal currentBodyFat = nonNull(baseline == null ? null : baseline.getBodyFatPct(), profile.getBodyFatPct());
        BigDecimal currentMuscle = nonNull(baseline == null ? null : baseline.getMuscleMassKg(), profile.getMuscleMassKg());
        BigDecimal currentWaist = nonNull(baseline == null ? null : baseline.getWaistCm(), profile.getWaistCm());
        BigDecimal currentWater = nonNull(baseline == null ? null : baseline.getBodyWaterPct(), profile.getBodyWaterPct());
        int measurementIndex = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate current = date;
            Instant end = endOfDay(current);
            while (measurementIndex < measurements.size()
                    && !measurements.get(measurementIndex).getMeasuredAt().isAfter(end)) {
                BodyMeasurement m = measurements.get(measurementIndex);
                if (m.getWeightKg() != null) currentWeight = m.getWeightKg();
                if (m.getBodyFatPct() != null) currentBodyFat = m.getBodyFatPct();
                if (m.getMuscleMassKg() != null) currentMuscle = m.getMuscleMassKg();
                if (m.getWaistCm() != null) currentWaist = m.getWaistCm();
                if (m.getBodyWaterPct() != null) currentWater = m.getBodyWaterPct();
                measurementIndex++;
            }
            int intake = rangeMeals.stream()
                    .filter(meal -> meal.getDate().equals(current))
                    .mapToInt(Meal::getTotalKcal)
                    .sum();
            int burn = rangeWorkouts.stream()
                    .filter(workout -> workout.getDate().equals(current) && workout.isDone())
                    .mapToInt(WorkoutPlan::getTotalKcal)
                    .sum();
            series.add(new SeriesPoint(current, intake, burn,
                    currentWeight, currentBodyFat, currentMuscle, currentWaist, currentWater));
        }
        return new RangeStatsResponse(from, to, series);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal latestWeightOnOrBefore(AppUser user, LocalDate date) {
        return bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(user.getId(), endOfDay(date))
                .map(BodyMeasurement::getWeightKg)
                .filter(weight -> weight != null)
                .orElse(user.getProfile().getWeightKg());
    }

    private List<BodyMeasurement> measurementsForRange(Long userId, LocalDate from, LocalDate to) {
        return bodyMeasurements.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, startOfDay(from), endOfDay(to));
    }

    private BigDecimal nonNull(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private int targetKcal(Profile profile) {
        int base = profile.getBmrKcal() == null ? 1700 : profile.getBmrKcal();
        Goal goal = profile.getGoal();
        int adjustment = switch (goal == null ? Goal.maintain : goal) {
            case fat_loss -> -300;
            case muscle_gain -> 300;
            case maintain -> 0;
        };
        return Math.max(1200, base + adjustment);
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
    }
}
