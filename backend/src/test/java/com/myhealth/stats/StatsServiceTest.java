package com.myhealth.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.Gender;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock MealRepository meals;
    @Mock WorkoutPlanRepository workouts;

    StatsService service;
    AppUser owner;

    @BeforeEach
    void setUp() {
        service = new StatsService(meals, workouts);
        owner = new AppUser();
        owner.setEmail("a@b.c");
        owner.setPasswordHash("h");
        owner.setRole(Role.USER);
        setField(owner, "id", 1L);
        Profile p = new Profile();
        p.setGender(Gender.male);
        p.setHeightCm(new BigDecimal("175"));
        p.setWeightKg(new BigDecimal("70.0"));
        owner.setProfile(p);
    }

    private static void setField(Object t, String n, Object v) {
        try {
            Field f = t.getClass().getDeclaredField(n);
            f.setAccessible(true);
            f.set(t, v);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Meal meal(LocalDate date, int kcal, double protein, double fat, double carb) {
        Meal m = new Meal();
        m.setDate(date);
        m.setSlot("lunch");
        m.setItemsJson("[]");
        m.setTotalKcal(kcal);
        m.setTotalProtein(BigDecimal.valueOf(protein));
        m.setTotalFat(BigDecimal.valueOf(fat));
        m.setTotalCarb(BigDecimal.valueOf(carb));
        return m;
    }

    private WorkoutPlan workout(LocalDate date, int kcal, boolean done) {
        WorkoutPlan w = new WorkoutPlan();
        w.setDate(date);
        w.setCategory("abs");
        w.setItemsJson("[]");
        w.setTotalKcal(kcal);
        w.setDone(done);
        return w;
    }

    @Test
    void daily_sumsIntake_protein_fat_carb_acrossMultipleMeals() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of(
                meal(today, 360, 28.0, 14.0, 42.0),
                meal(today, 250, 12.0, 8.0, 30.0)));
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());

        DailyStatsResponse response = service.daily(owner, today);

        assertThat(response.intakeKcal()).isEqualTo(610);
        assertThat(response.protein()).isEqualByComparingTo("40.0");
        assertThat(response.fat()).isEqualByComparingTo("22.0");
        assertThat(response.carb()).isEqualByComparingTo("72.0");
        assertThat(response.weightKg()).isEqualByComparingTo("70.0");
    }

    @Test
    void daily_burn_onlyCountsCompletedWorkouts() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of(
                workout(today, 130, true),
                workout(today, 180, false),
                workout(today, 90, true)));

        DailyStatsResponse response = service.daily(owner, today);

        assertThat(response.burnKcal()).isEqualTo(220);  // only the two done
        assertThat(response.workoutsDone()).isEqualTo(2);
        assertThat(response.workoutsPlanned()).isEqualTo(3);
    }

    @Test
    void daily_netKcalIsIntakeMinusBurn() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of(meal(today, 500, 0, 0, 0)));
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of(workout(today, 200, true)));

        DailyStatsResponse response = service.daily(owner, today);

        assertThat(response.netKcal()).isEqualTo(300);
    }

    @Test
    void daily_returnsZeros_whenEmpty() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());

        DailyStatsResponse response = service.daily(owner, today);

        assertThat(response.intakeKcal()).isZero();
        assertThat(response.burnKcal()).isZero();
        assertThat(response.netKcal()).isZero();
        assertThat(response.protein()).isEqualByComparingTo("0");
    }

    @Test
    void range_emitsContiguousSeriesIncludingEmptyDays() {
        LocalDate from = LocalDate.of(2026, 5, 28);
        LocalDate to = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of(
                meal(LocalDate.of(2026, 5, 30), 720, 0, 0, 0)));
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of(
                workout(LocalDate.of(2026, 5, 29), 150, true),
                workout(LocalDate.of(2026, 5, 29), 100, false)));  // not done

        RangeStatsResponse response = service.range(owner, from, to);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
        assertThat(response.series()).hasSize(3);
        assertThat(response.series().get(0).date()).isEqualTo(from);
        assertThat(response.series().get(0).intakeKcal()).isZero();
        assertThat(response.series().get(0).burnKcal()).isZero();
        assertThat(response.series().get(1).burnKcal()).isEqualTo(150);
        assertThat(response.series().get(2).intakeKcal()).isEqualTo(720);
    }

    @Test
    void range_clampsTo90DayWindow() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);  // ~180 days out

        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(from.plusDays(90))))
                .thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(from.plusDays(90))))
                .thenReturn(List.of());

        RangeStatsResponse response = service.range(owner, from, to);

        // from + 90 days = 2026-04-01
        assertThat(response.to()).isEqualTo(from.plusDays(90));
        assertThat(response.series()).hasSize(91);  // inclusive of both endpoints
    }

    @Test
    void range_singleDay_emitsOnePoint() {
        LocalDate d = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, d, d)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, d, d)).thenReturn(List.of());

        RangeStatsResponse response = service.range(owner, d, d);

        assertThat(response.series()).hasSize(1);
        assertThat(response.series().get(0).date()).isEqualTo(d);
    }

}
