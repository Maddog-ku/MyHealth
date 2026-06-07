package com.myhealth.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.export.ExportDtos.ExportFile;
import com.myhealth.goal.WeightGoal;
import com.myhealth.goal.WeightGoalRepository;
import com.myhealth.habit.HabitLog;
import com.myhealth.habit.HabitLogRepository;
import com.myhealth.habit.HabitType;
import com.myhealth.meal.FavoriteMealRepository;
import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.user.AppUser;
import com.myhealth.user.BodyMeasurement;
import com.myhealth.user.BodyMeasurementRepository;
import com.myhealth.user.Gender;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataExportServiceTest {

    @Mock BodyMeasurementRepository measurements;
    @Mock WorkoutPlanRepository workouts;
    @Mock MealRepository meals;
    @Mock WeightGoalRepository weightGoals;
    @Mock FavoriteMealRepository favoriteMeals;
    @Mock HabitLogRepository habits;

    final ObjectMapper objectMapper = new ObjectMapper();
    DataExportService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new DataExportService(measurements, workouts, meals, weightGoals,
                favoriteMeals, habits, objectMapper);
        user = new AppUser();
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setRole(Role.USER);
        Profile p = new Profile();
        p.setGender(Gender.female);
        user.setProfile(p);
        setId(user, 1L);
    }

    @Test
    void export_collectsAllDatasets_andEmbedsItemsAsNestedJson() {
        BodyMeasurement m = new BodyMeasurement();
        m.setWeightKg(new BigDecimal("55.0"));
        m.setBmrKcal(1400);
        m.setNote("registration");
        when(measurements.findByUserIdOrderByMeasuredAtAsc(eq(1L))).thenReturn(List.of(m));

        WorkoutPlan w = new WorkoutPlan();
        w.setDate(LocalDate.of(2026, 6, 1));
        w.setCategory("legs");
        w.setItemsJson("[{\"name\":\"深蹲\",\"sets\":4}]");
        w.setTotalKcal(200);
        w.setDone(true);
        when(workouts.findByUserIdOrderByDateAscCreatedAtAsc(eq(1L))).thenReturn(List.of(w));

        Meal meal = new Meal();
        meal.setDate(LocalDate.of(2026, 6, 1));
        meal.setSlot("lunch");
        meal.setItemsJson("[{\"name\":\"雞胸肉\",\"kcal\":248}]");
        meal.setTotalKcal(248);
        when(meals.findByUserIdOrderByDateAscCreatedAtAsc(eq(1L))).thenReturn(List.of(meal));

        when(weightGoals.findByUserId(eq(1L))).thenReturn(Optional.of(new WeightGoal(
                user, new BigDecimal("50"), new BigDecimal("55"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1))));

        when(favoriteMeals.findByUserIdOrderByCreatedAtDesc(eq(1L))).thenReturn(List.of());

        HabitLog h = new HabitLog();
        h.setDate(LocalDate.of(2026, 6, 1));
        h.setType(HabitType.WATER);
        when(habits.findByUserIdOrderByDateAscTypeAsc(eq(1L))).thenReturn(List.of(h));

        ExportFile file = service.export(user);

        assertThat(file.exportedAt()).isNotBlank();
        assertThat(file.account().email()).isEqualTo("alice@example.com");
        assertThat(file.account().profile()).isNotNull();

        assertThat(file.bodyMeasurements()).hasSize(1);
        assertThat(file.bodyMeasurements().get(0).weightKg()).isEqualByComparingTo("55.0");
        assertThat(file.bodyMeasurements().get(0).bmrKcal()).isEqualTo(1400);

        // Workout/meal item arrays are embedded as real nested JSON, not escaped strings.
        assertThat(file.workouts()).hasSize(1);
        assertThat(file.workouts().get(0).items().isArray()).isTrue();
        assertThat(file.workouts().get(0).items().get(0).get("name").asText()).isEqualTo("深蹲");

        assertThat(file.meals()).hasSize(1);
        assertThat(file.meals().get(0).items().get(0).get("kcal").asInt()).isEqualTo(248);

        assertThat(file.weightGoal()).isNotNull();
        assertThat(file.weightGoal().targetWeightKg()).isEqualByComparingTo("50");

        assertThat(file.favoriteMeals()).isEmpty();
        assertThat(file.habits()).hasSize(1);
        assertThat(file.habits().get(0).type()).isEqualTo("WATER");
    }

    @Test
    void export_handlesEmptyAccount_andNullWeightGoal() {
        when(measurements.findByUserIdOrderByMeasuredAtAsc(eq(1L))).thenReturn(List.of());
        when(workouts.findByUserIdOrderByDateAscCreatedAtAsc(eq(1L))).thenReturn(List.of());
        when(meals.findByUserIdOrderByDateAscCreatedAtAsc(eq(1L))).thenReturn(List.of());
        when(weightGoals.findByUserId(eq(1L))).thenReturn(Optional.empty());
        when(favoriteMeals.findByUserIdOrderByCreatedAtDesc(eq(1L))).thenReturn(List.of());
        when(habits.findByUserIdOrderByDateAscTypeAsc(eq(1L))).thenReturn(List.of());

        ExportFile file = service.export(user);

        assertThat(file.weightGoal()).isNull();
        assertThat(file.workouts()).isEmpty();
        assertThat(file.meals()).isEmpty();
        assertThat(file.bodyMeasurements()).isEmpty();
    }

    @Test
    void export_fallsBackToEmptyArray_whenItemsJsonMalformed() {
        WorkoutPlan w = new WorkoutPlan();
        w.setDate(LocalDate.of(2026, 6, 1));
        w.setCategory("legs");
        w.setItemsJson("not-json");
        w.setTotalKcal(0);
        when(measurements.findByUserIdOrderByMeasuredAtAsc(eq(1L))).thenReturn(List.of());
        when(workouts.findByUserIdOrderByDateAscCreatedAtAsc(eq(1L))).thenReturn(List.of(w));
        when(meals.findByUserIdOrderByDateAscCreatedAtAsc(eq(1L))).thenReturn(List.of());
        when(weightGoals.findByUserId(eq(1L))).thenReturn(Optional.empty());
        when(favoriteMeals.findByUserIdOrderByCreatedAtDesc(eq(1L))).thenReturn(List.of());
        when(habits.findByUserIdOrderByDateAscTypeAsc(eq(1L))).thenReturn(List.of());

        ExportFile file = service.export(user);

        assertThat(file.workouts().get(0).items().isArray()).isTrue();
        assertThat(file.workouts().get(0).items()).isEmpty();
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
