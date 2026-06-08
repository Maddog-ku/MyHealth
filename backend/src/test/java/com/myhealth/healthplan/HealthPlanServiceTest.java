package com.myhealth.healthplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.myhealth.goal.GoalDtos.WeightGoalProgress;
import com.myhealth.goal.GoalDtos.WeightGoalResponse;
import com.myhealth.goal.GoalService;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanResponse;
import com.myhealth.stats.StatsDtos.CalorieBudgetResponse;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.stats.StatsService;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakService;
import com.myhealth.user.AppUser;
import com.myhealth.user.Goal;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import com.myhealth.user.UserRepository;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalResponse;
import com.myhealth.workout.WorkoutGoalService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthPlanServiceTest {
    @Mock StatsService stats;
    @Mock GoalService weightGoals;
    @Mock WorkoutGoalService workoutGoals;
    @Mock StreakService streaks;
    @Mock UserRepository users;

    HealthPlanService service;
    AppUser user;
    LocalDate date;

    @BeforeEach
    void setUp() {
        service = new HealthPlanService(stats, weightGoals, workoutGoals, streaks, users);
        user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        Profile profile = new Profile();
        profile.setGoal(Goal.fat_loss);
        profile.setHeightCm(new BigDecimal("170"));
        profile.setWeightKg(new BigDecimal("76.0"));
        user.setProfile(profile);
        date = LocalDate.of(2026, 6, 8);
    }

    @Test
    void get_prioritizesSetupActions_whenGoalsAreMissing() {
        givenStats(0, false, 0);
        when(weightGoals.get(user)).thenReturn(new WeightGoalResponse(null));
        when(workoutGoals.get(user)).thenReturn(new WorkoutGoalResponse(null));
        when(streaks.overallStreak(user)).thenReturn(new StreakInfo(0, 4, date.minusDays(3)));

        HealthPlanResponse response = service.get(user, date);

        assertThat(response.primaryGoal()).isEqualTo("減脂");
        assertThat(response.weight().configured()).isFalse();
        assertThat(response.workout().configured()).isFalse();
        assertThat(response.nextActions()).extracting("type")
                .containsExactly("SET_WEIGHT_GOAL", "SET_WORKOUT_GOAL", "ADD_PROTEIN", "LOG_MEAL");
        assertThat(response.readinessScore()).isBetween(0, 100);
    }

    @Test
    void get_flagsWeightPaceWorkoutRemainingAndProteinGap() {
        givenStats(1200, false, 45);
        when(weightGoals.get(user)).thenReturn(new WeightGoalResponse(new WeightGoalProgress(
                new BigDecimal("70.0"), new BigDecimal("78.0"), new BigDecimal("76.0"),
                date.minusDays(30), date.plusDays(20), new BigDecimal("-6.0"),
                new BigDecimal("-2.0"), 25, -0.5, date.plusDays(84),
                false, false, Instant.parse("2026-05-09T00:00:00Z"))));
        when(workoutGoals.get(user)).thenReturn(new WorkoutGoalResponse(new WorkoutGoalProgress(
                4, 2, 2, 50, date.minusDays(0), false,
                Instant.parse("2026-05-09T00:00:00Z"))));
        when(streaks.overallStreak(user)).thenReturn(new StreakInfo(3, 8, date));

        HealthPlanResponse response = service.get(user, date);

        assertThat(response.weight().onTrack()).isFalse();
        assertThat(response.workout().remainingThisWeek()).isEqualTo(2);
        assertThat(response.nextActions()).extracting("type")
                .containsExactly("REVIEW_WEIGHT_GOAL", "PLAN_WORKOUT", "ADD_PROTEIN");
    }

    @Test
    void updateSettings_updatesPrimaryGoalAndDelegatesTargets() {
        when(stats.daily(user, LocalDate.now())).thenReturn(new DailyStatsResponse(
                LocalDate.now(), 1200, 250, 950,
                new BigDecimal("50.0"), new BigDecimal("30.0"), new BigDecimal("120.0"),
                new BigDecimal("76.0"), 1700, 1, 1));
        when(weightGoals.set(user, new com.myhealth.goal.GoalDtos.SetWeightGoalRequest(new BigDecimal("70.0"), date.plusDays(60))))
                .thenReturn(new WeightGoalResponse(new WeightGoalProgress(
                        new BigDecimal("70.0"), new BigDecimal("76.0"), new BigDecimal("76.0"),
                        date, date.plusDays(60), new BigDecimal("-6.0"),
                        BigDecimal.ZERO, 0, null, null, null, false,
                        Instant.parse("2026-06-08T00:00:00Z"))));
        when(workoutGoals.set(user, new com.myhealth.workout.WorkoutGoalDtos.SetWorkoutGoalRequest(4)))
                .thenReturn(new WorkoutGoalResponse(new WorkoutGoalProgress(4, 0, 4, 0, date, false,
                        Instant.parse("2026-06-08T00:00:00Z"))));
        when(weightGoals.get(user)).thenReturn(new WeightGoalResponse(null));
        when(workoutGoals.get(user)).thenReturn(new WorkoutGoalResponse(null));

        service.updateSettings(user, new HealthPlanDtos.HealthPlanSettingsRequest(
                Goal.muscle_gain,
                new HealthPlanDtos.WeightGoalSettings(true, new BigDecimal("70.0"), date.plusDays(60)),
                new HealthPlanDtos.WorkoutGoalSettings(true, 4)));

        assertThat(user.getProfile().getGoal()).isEqualTo(Goal.muscle_gain);
    }

    private void givenStats(int intake, boolean over, int proteinPct) {
        when(stats.daily(user, date)).thenReturn(new DailyStatsResponse(
                date, intake, 250, intake - 250,
                new BigDecimal("50.0"), new BigDecimal("30.0"), new BigDecimal("120.0"),
                new BigDecimal("76.0"), 1700, 1, 1));
        when(stats.budget(user, date)).thenReturn(new CalorieBudgetResponse(
                date, 1700, intake, 250, 1950, 1950 - intake,
                Math.round(intake * 100f / 1950), over,
                List.of(new MacroBudget("protein", 149, Math.round(149 * proteinPct / 100f), proteinPct),
                        new MacroBudget("carb", 149, 100, 67),
                        new MacroBudget("fat", 57, 30, 53))));
    }
}
