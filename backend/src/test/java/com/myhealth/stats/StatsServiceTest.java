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
    void daily_burn_usesPartialBurnedKcal_whenRecorded() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());
        WorkoutPlan partial = workout(today, 200, true);
        partial.setBurnedKcal(70);  // only some exercises were actually completed
        WorkoutPlan legacy = workout(today, 50, true);  // burnedKcal null → falls back to total
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of(partial, legacy));

        DailyStatsResponse response = service.daily(owner, today);

        assertThat(response.burnKcal()).isEqualTo(120);  // 70 (partial) + 50 (legacy total)
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
    void range_partialMeasurement_keepsPreviouslyCarriedFields() {
        // A later measurement that only updates weight (bodyFat null) must NOT erase the
        // body-fat value carried from the earlier full snapshot. This locks the contract
        // that the series carries each metric forward independently.
        LocalDate from = LocalDate.of(2026, 5, 28);
        LocalDate to = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(bodyMeasurements.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        measurement("2026-05-28T03:00:00Z", "70.0", "22.0"),  // full snapshot
                        measurement("2026-05-29T03:00:00Z", "69.5")));         // weight only, bodyFat null

        RangeStatsResponse response = service.range(owner, from, to);

        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::weightKg)
                .containsExactly(new BigDecimal("70.0"), new BigDecimal("69.5"), new BigDecimal("69.5"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::bodyFatPct)
                .containsExactly(new BigDecimal("22.0"), new BigDecimal("22.0"), new BigDecimal("22.0"));
    }

    @Test
    void range_carriesForwardMuscleWaistAndWater() {
        LocalDate from = LocalDate.of(2026, 5, 28);
        LocalDate to = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        BodyMeasurement m = new BodyMeasurement();
        m.setMeasuredAt(Instant.parse("2026-05-29T03:00:00Z"));
        m.setMuscleMassKg(new BigDecimal("30.0"));
        m.setWaistCm(new BigDecimal("80.0"));
        m.setBodyWaterPct(new BigDecimal("55.0"));
        when(bodyMeasurements.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(m));

        RangeStatsResponse response = service.range(owner, from, to);

        // Day 1 has no measurement yet (and profile carries none) → null; then each
        // metric carries forward from the measured day onward.
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::muscleMassKg)
                .containsExactly(null, new BigDecimal("30.0"), new BigDecimal("30.0"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::waistCm)
                .containsExactly(null, new BigDecimal("80.0"), new BigDecimal("80.0"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::bodyWaterPct)
                .containsExactly(null, new BigDecimal("55.0"), new BigDecimal("55.0"));
    }

    @Test
    void range_seedsAllMetricsFromPreRangeBaseline() {
        // The most recent snapshot BEFORE the range seeds the starting values for every
        // metric, so day 1 already shows them even with no in-range measurements.
        LocalDate from = LocalDate.of(2026, 5, 28);
        LocalDate to = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of());
        BodyMeasurement baseline = new BodyMeasurement();
        baseline.setMeasuredAt(Instant.parse("2026-05-25T03:00:00Z"));  // before `from`
        baseline.setWeightKg(new BigDecimal("71.0"));
        baseline.setBodyFatPct(new BigDecimal("23.0"));
        baseline.setMuscleMassKg(new BigDecimal("31.0"));
        baseline.setWaistCm(new BigDecimal("82.0"));
        baseline.setBodyWaterPct(new BigDecimal("54.0"));
        when(bodyMeasurements.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(eq(1L), any(Instant.class)))
                .thenReturn(Optional.of(baseline));

        RangeStatsResponse response = service.range(owner, from, to);

        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::weightKg)
                .containsExactly(new BigDecimal("71.0"), new BigDecimal("71.0"), new BigDecimal("71.0"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::bodyFatPct)
                .containsExactly(new BigDecimal("23.0"), new BigDecimal("23.0"), new BigDecimal("23.0"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::muscleMassKg)
                .containsExactly(new BigDecimal("31.0"), new BigDecimal("31.0"), new BigDecimal("31.0"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::waistCm)
                .containsExactly(new BigDecimal("82.0"), new BigDecimal("82.0"), new BigDecimal("82.0"));
        assertThat(response.series()).extracting(StatsDtos.SeriesPoint::bodyWaterPct)
                .containsExactly(new BigDecimal("54.0"), new BigDecimal("54.0"), new BigDecimal("54.0"));
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

    @Test
    void budget_addsExerciseBack_andSplitsMacrosByGoal() {
        // No goal → maintain → target 1700 kcal; eat 1200, burn 300.
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today))
                .thenReturn(List.of(meal(today, 1200, 90.0, 40.0, 120.0)));
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today))
                .thenReturn(List.of(workout(today, 300, true)));

        StatsDtos.CalorieBudgetResponse b = service.budget(owner, today);

        assertThat(b.goalKcal()).isEqualTo(1700);
        assertThat(b.budgetKcal()).isEqualTo(2000);   // 1700 + 300 earned back
        assertThat(b.remainingKcal()).isEqualTo(800);
        assertThat(b.consumedPct()).isEqualTo(60);     // 1200 / 2000
        assertThat(b.over()).isFalse();
        // maintain split 30/40/30 of 1700: P=128g, C=170g, F=57g.
        assertThat(b.macros()).extracting(StatsDtos.MacroBudget::name, StatsDtos.MacroBudget::targetG,
                        StatsDtos.MacroBudget::consumedG)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("protein", 128, 90),
                        org.assertj.core.groups.Tuple.tuple("carb", 170, 120),
                        org.assertj.core.groups.Tuple.tuple("fat", 57, 40));
    }

    @Test
    void budget_flagsOver_whenIntakeExceedsBudget() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today))
                .thenReturn(List.of(meal(today, 2200, 0, 0, 0)));
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());

        StatsDtos.CalorieBudgetResponse b = service.budget(owner, today);

        assertThat(b.budgetKcal()).isEqualTo(1700);
        assertThat(b.remainingKcal()).isEqualTo(-500);
        assertThat(b.consumedPct()).isEqualTo(129);
        assertThat(b.over()).isTrue();
    }

    @Test
    void budget_usesFatLossTargetAndProteinSplit() {
        owner.getProfile().setGoal(Goal.fat_loss); // target 1700 - 300 = 1400, protein 35%
        LocalDate today = LocalDate.of(2026, 5, 30);
        when(meals.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, today)).thenReturn(List.of());

        StatsDtos.CalorieBudgetResponse b = service.budget(owner, today);

        assertThat(b.goalKcal()).isEqualTo(1400);
        assertThat(b.macros().get(0).name()).isEqualTo("protein");
        assertThat(b.macros().get(0).targetG()).isEqualTo(122); // round(1400 * 0.35 / 4)
        assertThat(b.macros().get(0).consumedG()).isZero();
    }

}
