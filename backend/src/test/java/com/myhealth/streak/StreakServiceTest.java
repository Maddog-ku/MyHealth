package com.myhealth.streak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.meal.MealRepository;
import com.myhealth.streak.StreakDtos.AchievementView;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakDtos.StreakSummaryResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutGoal;
import com.myhealth.workout.WorkoutGoalRepository;
import com.myhealth.workout.WorkoutPlanRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreakServiceTest {

    @Mock MealRepository meals;
    @Mock WorkoutPlanRepository workouts;
    @Mock WorkoutGoalRepository workoutGoals;
    @Mock BodyMeasurementRepository bodyMeasurements;
    @Mock AchievementService achievements;

    StreakService service;
    AppUser user;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new StreakService(meals, workouts, workoutGoals, bodyMeasurements, achievements);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
        lenient().when(achievements.reconcile(any(), any()))
                .thenReturn(new AchievementService.ReconcileResult(List.of(), List.of()));
    }

    // ---- core algorithm (streakOf) ----

    @Test
    void streakOf_empty_isZero() {
        StreakInfo s = StreakService.streakOf(Set.of(), TODAY);
        assertThat(s.current()).isZero();
        assertThat(s.longest()).isZero();
        assertThat(s.lastActiveDate()).isNull();
    }

    @Test
    void streakOf_consecutiveDaysEndingToday() {
        Set<LocalDate> days = days(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));
        StreakInfo s = StreakService.streakOf(days, TODAY);
        assertThat(s.current()).isEqualTo(3);
        assertThat(s.longest()).isEqualTo(3);
        assertThat(s.lastActiveDate()).isEqualTo(TODAY);
    }

    @Test
    void streakOf_givesTodayAGraceDay_whenOnlyYesterdayActive() {
        // No activity today yet, but yesterday + the day before form a run → still "alive".
        Set<LocalDate> days = days(TODAY.minusDays(1), TODAY.minusDays(2));
        StreakInfo s = StreakService.streakOf(days, TODAY);
        assertThat(s.current()).isEqualTo(2);
        assertThat(s.lastActiveDate()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void streakOf_currentBreaks_butLongestReflectsPastRun() {
        // Active today (current = 1), plus a separate 4-day run two weeks ago.
        Set<LocalDate> days = days(TODAY,
                TODAY.minusDays(14), TODAY.minusDays(13), TODAY.minusDays(12), TODAY.minusDays(11));
        StreakInfo s = StreakService.streakOf(days, TODAY);
        assertThat(s.current()).isEqualTo(1);
        assertThat(s.longest()).isEqualTo(4);
        assertThat(s.lastActiveDate()).isEqualTo(TODAY);
    }

    @Test
    void streakOf_zeroCurrent_whenNeitherTodayNorYesterdayActive() {
        Set<LocalDate> days = days(TODAY.minusDays(3), TODAY.minusDays(4));
        StreakInfo s = StreakService.streakOf(days, TODAY);
        assertThat(s.current()).isZero();
        assertThat(s.longest()).isEqualTo(2);
    }

    // ---- getSummary wiring ----

    @Test
    void getSummary_computesStreaks_andFeedsMetricsToAchievements() {
        // Repos project just the dates/timestamps (done-filter + distinct happen in SQL).
        when(meals.findDistinctMealDates(eq(1L), any(), any()))
                .thenReturn(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)));
        when(workouts.findDistinctDoneWorkoutDates(eq(1L), any(), any()))
                .thenReturn(List.of(TODAY, TODAY.minusDays(1)));
        when(bodyMeasurements.findMeasuredAtBetween(eq(1L), any(), any()))
                .thenReturn(List.of(TODAY.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));
        when(meals.countByUserId(1L)).thenReturn(60L);
        when(workouts.countByUserIdAndDoneTrue(1L)).thenReturn(12L);
        when(bodyMeasurements.countByUserId(1L)).thenReturn(1L);
        when(meals.countPhotoMeals(1L)).thenReturn(7L);
        // Three sessions this week against a target of 2 → one weekly-goal hit.
        when(workouts.findDoneWorkoutDates(eq(1L), any(), any()))
                .thenReturn(List.of(TODAY, TODAY, TODAY));
        when(workoutGoals.findByUserId(1L)).thenReturn(Optional.of(new WorkoutGoal(user, 2)));

        AchievementView view = new AchievementView("STREAK_3", "三日連勝", "🔥", "x", 3, 3, true, null);
        when(achievements.reconcile(eq(user), any()))
                .thenReturn(new AchievementService.ReconcileResult(List.of(view), List.of("STREAK_3")));

        StreakSummaryResponse res = service.getSummary(user);

        assertThat(res.mealStreak().current()).isEqualTo(3);   // 3 consecutive meal days
        assertThat(res.workoutStreak().current()).isEqualTo(2); // today + yesterday done
        assertThat(res.overallStreak().current()).isEqualTo(3);
        assertThat(res.achievements()).containsExactly(view);
        assertThat(res.newlyUnlocked()).containsExactly("STREAK_3");

        ArgumentCaptor<StreakMetrics> metrics = ArgumentCaptor.forClass(StreakMetrics.class);
        verify(achievements).reconcile(eq(user), metrics.capture());
        assertThat(metrics.getValue().mealCount()).isEqualTo(60L);
        assertThat(metrics.getValue().workoutCount()).isEqualTo(12L);
        assertThat(metrics.getValue().weightCount()).isEqualTo(1L);
        assertThat(metrics.getValue().longestStreak()).isEqualTo(3); // overall longest
        assertThat(metrics.getValue().photoMealCount()).isEqualTo(7L);
        assertThat(metrics.getValue().weeklyGoalHits()).isEqualTo(1); // 3 sessions ≥ target 2 in one week
    }

    @Test
    void weeklyGoalHits_countsOnlyWeeksMeetingTarget() {
        LocalDate w1 = LocalDate.of(2026, 6, 1);  // Monday
        LocalDate w2 = w1.plusDays(7);
        // Week 1: 3 sessions (≥2 → hit). Week 2: 1 session (<2 → miss).
        List<LocalDate> done = List.of(w1, w1.plusDays(2), w1.plusDays(4), w2.plusDays(1));
        assertThat(StreakService.weeklyGoalHits(done, 2)).isEqualTo(1);
        assertThat(StreakService.weeklyGoalHits(done, 1)).isEqualTo(2);
        assertThat(StreakService.weeklyGoalHits(done, 0)).isZero(); // no goal set
    }

    private static Set<LocalDate> days(LocalDate... d) {
        return new TreeSet<>(Set.of(d));
    }

    private static void setId(AppUser user, Long id) {
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
