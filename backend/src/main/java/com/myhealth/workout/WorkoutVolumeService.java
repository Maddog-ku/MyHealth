package com.myhealth.workout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.common.JsonColumns;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutVolumeDtos.CategoryVolume;
import com.myhealth.workout.WorkoutVolumeDtos.VolumeResponse;
import com.myhealth.workout.WorkoutVolumeDtos.WeekVolume;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates completed workouts over the last N weeks into training-volume analytics:
 * totals, a per-category breakdown (to spot muscle-group imbalance), and a Monday-aligned
 * weekly series for trend charts. Read-only; computed live from {@link WorkoutPlan} rows.
 */
@Service
public class WorkoutVolumeService {
    /** Bounds on how many trailing weeks the caller may request. */
    static final int MIN_WEEKS = 1;
    static final int MAX_WEEKS = 12;
    private static final int DEFAULT_WEEKS = 4;

    /**
     * Primary muscle groups we expect a balanced routine to touch. cardio and full_body are
     * left out — they aren't a single group — so "neglected" means a targeted group went
     * untrained in the range. Ordered so the nudge reads consistently.
     */
    private static final List<String> PRIMARY_MUSCLE_GROUPS =
            List.of("legs", "chest", "back", "arms", "abs", "glutes");

    private final WorkoutPlanRepository workouts;
    private final ObjectMapper objectMapper;

    public WorkoutVolumeService(WorkoutPlanRepository workouts, ObjectMapper objectMapper) {
        this.workouts = workouts;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public VolumeResponse volume(AppUser user, Integer weeksInput) {
        int weeks = clampWeeks(weeksInput);
        LocalDate today = LocalDate.now();
        LocalDate thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fromWeekStart = thisWeekStart.minusWeeks(weeks - 1L);

        // Only completed sessions count toward training volume.
        List<WorkoutPlan> done = workouts.findByUserIdAndDateBetweenOrderByDateAsc(
                        user.getId(), fromWeekStart, today).stream()
                .filter(WorkoutPlan::isDone)
                .toList();

        // Pre-seed every week bucket so the trend series is continuous even with gaps.
        WeekAccumulator[] buckets = new WeekAccumulator[weeks];
        for (int i = 0; i < weeks; i++) {
            buckets[i] = new WeekAccumulator(fromWeekStart.plusWeeks(i));
        }

        Map<String, CategoryAccumulator> byCategory = new LinkedHashMap<>();
        Set<LocalDate> activeDays = new HashSet<>();
        int totalSessions = 0;
        int totalSets = 0;
        int totalKcal = 0;

        for (WorkoutPlan plan : done) {
            int sets = totalSets(plan);
            int kcal = plan.effectiveBurnKcal();

            totalSessions++;
            totalSets += sets;
            totalKcal += kcal;
            activeDays.add(plan.getDate());

            int weekIndex = (int) java.time.temporal.ChronoUnit.WEEKS.between(
                    fromWeekStart, plan.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (weekIndex >= 0 && weekIndex < weeks) {
                buckets[weekIndex].add(sets, kcal);
            }

            byCategory.computeIfAbsent(plan.getCategory(), CategoryAccumulator::new).add(sets, kcal);
        }

        List<CategoryVolume> categories = byCategory.values().stream()
                .map(CategoryAccumulator::toDto)
                .sorted(Comparator.comparingInt(CategoryVolume::sessions).reversed()
                        .thenComparing(Comparator.comparingInt(CategoryVolume::kcal).reversed()))
                .toList();

        List<WeekVolume> series = new ArrayList<>(weeks);
        for (WeekAccumulator b : buckets) {
            series.add(b.toDto());
        }

        double avgPerWeek = Math.round((double) totalSessions / weeks * 10.0) / 10.0;

        List<String> neglected = PRIMARY_MUSCLE_GROUPS.stream()
                .filter(group -> !byCategory.containsKey(group))
                .toList();

        return new VolumeResponse(fromWeekStart, today, weeks, totalSessions, totalSets, totalKcal,
                activeDays.size(), avgPerWeek, categories, series, neglected);
    }

    private int clampWeeks(Integer weeks) {
        if (weeks == null) {
            return DEFAULT_WEEKS;
        }
        return Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));
    }

    private int totalSets(WorkoutPlan plan) {
        List<ExerciseItem> items = JsonColumns.read(objectMapper, plan.getItemsJson(),
                new TypeReference<List<ExerciseItem>>() {
                });
        return items.stream().mapToInt(ExerciseItem::sets).sum();
    }

    private static final class WeekAccumulator {
        private final LocalDate weekStart;
        private int sessions;
        private int sets;
        private int kcal;

        WeekAccumulator(LocalDate weekStart) {
            this.weekStart = weekStart;
        }

        void add(int sets, int kcal) {
            this.sessions++;
            this.sets += sets;
            this.kcal += kcal;
        }

        WeekVolume toDto() {
            return new WeekVolume(weekStart, sessions, sets, kcal);
        }
    }

    private static final class CategoryAccumulator {
        private final String category;
        private int sessions;
        private int sets;
        private int kcal;

        CategoryAccumulator(String category) {
            this.category = category;
        }

        void add(int sets, int kcal) {
            this.sessions++;
            this.sets += sets;
            this.kcal += kcal;
        }

        CategoryVolume toDto() {
            return new CategoryVolume(category, sessions, sets, kcal);
        }
    }
}
