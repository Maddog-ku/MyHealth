package com.myhealth.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.common.ApiException;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Gender;
import com.myhealth.user.Goal;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock MealRepository meals;
    @Mock WorkoutPlanRepository workouts;
    @Mock BodyMeasurementRepository bodyMeasurements;

    StatsService service;
    AppUser owner;

    @BeforeEach
    void setUp() {
        service = new StatsService(meals, workouts, bodyMeasurements);
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
        lenient().when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(eq(1L), any(Instant.class)))
                .thenReturn(Optional.empty());
        lenient().when(bodyMeasurements.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
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

    private BodyMeasurement measurement(String measuredAt, String weightKg) {
        BodyMeasurement m = new BodyMeasurement();
        m.setMeasuredAt(Instant.parse(measuredAt));
        m.setWeightKg(new BigDecimal(weightKg));
        return m;
    }

    private BodyMeasurement measurement(String measuredAt, String weightKg, String bodyFatPct) {
        BodyMeasurement m = measurement(measuredAt, weightKg);
        m.setBodyFatPct(new BigDecimal(bodyFatPct));
        return m;
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
    void daily_usesLatestMeasurementWeightAndGoalAdjustedTargetKcal() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        owner.getProfile().setBmrKcal(1800);
        owner.getProfile().setGoal(Goal.fat_loss);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(eq(1L), any(Instant.class)))
                .thenReturn(Optional.of(measurement("2026-05-29T12:00:00Z", "68.5")));

        DailyStatsResponse response = service.daily(owner, today);

        assertThat(response.weightKg()).isEqualByComparingTo("68.5");
        assertThat(response.goalKcal()).isEqualTo(1500);
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
    void range_carriesForwardHistoricalMeasurementWeights() {
        LocalDate from = LocalDate.of(2026, 5, 28);
        LocalDate to = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(eq(1L), any(Instant.class)))
                .thenReturn(Optional.of(measurement("2026-05-27T12:00:00Z", "70.2")));
        when(bodyMeasurements.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        measurement("2026-05-29T03:00:00Z", "69.8"),
                        measurement("2026-05-30T03:00:00Z", "69.5")));

        RangeStatsResponse response = service.range(owner, from, to);

        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::weightKg)
                .containsExactly(new BigDecimal("70.2"), new BigDecimal("69.8"), new BigDecimal("69.5"));
    }

    @Test
    void range_carriesForwardBodyFatMeasurements() {
        LocalDate from = LocalDate.of(2026, 5, 28);
        LocalDate to = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(bodyMeasurements.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        measurement("2026-05-29T03:00:00Z", "69.8", "22.5"),
                        measurement("2026-05-30T03:00:00Z", "69.5", "22.1")));

        RangeStatsResponse response = service.range(owner, from, to);

        // Day 1 has no measurement yet (and no profile body fat) → null; then it
        // carries forward each measured value.
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::bodyFatPct)
                .containsExactly(null, new BigDecimal("22.5"), new BigDecimal("22.1"));
    }

    @Test
    void range_rejectsMoreThan90Days() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);  // ~180 days out

        assertThatThrownBy(() -> service.range(owner, from, to))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("90 days");
    }

    @Test
    void range_rejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.range(owner, LocalDate.of(2026, 5, 30), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("from must be on or before to");
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
