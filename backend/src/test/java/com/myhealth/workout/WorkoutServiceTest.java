package com.myhealth.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock WorkoutPlanRepository workouts;
    @Mock AiProvider aiProvider;

    final ObjectMapper objectMapper = new ObjectMapper();
    WorkoutService service;
    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        // Run the persist callback inline (no real transaction). lenient() because not every
        // test exercises the generate() path that calls transactionTemplate.execute.
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new WorkoutService(workouts, aiProvider, objectMapper, transactionTemplate);
        owner = userWithId(1L);
        other = userWithId(99L);
    }

    private AppUser userWithId(long id) {
        AppUser u = new AppUser();
        u.setEmail("u" + id + "@example.com");
        u.setPasswordHash("h");
        u.setRole(Role.USER);
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return u;
    }

    @Test
    void generate_callsAi_persistsPlan_andSumsTotalKcal() {
        List<ExerciseItem> aiItems = List.of(
                new ExerciseItem("捲腹", 4, "15", 45, 45, 40, "n", List.of("死蟲式")),
                new ExerciseItem("棒式", 3, "45s", 45, 45, 35, "n", List.of()),
                new ExerciseItem("登山者", 3, "30s", 60, 30, 55, "n", List.of()));
        when(aiProvider.generateWorkout("abs", 30, "medium")).thenReturn(aiItems);
        when(workouts.save(any(WorkoutPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutPlanResponse response = service.generate(owner,
                new GenerateWorkoutRequest(LocalDate.of(2026, 5, 30), WorkoutCategory.abs, 30, WorkoutIntensity.medium, null));

        ArgumentCaptor<WorkoutPlan> captor = ArgumentCaptor.forClass(WorkoutPlan.class);
        verify(workouts).save(captor.capture());
        WorkoutPlan saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 5, 30));
        assertThat(saved.getCategory()).isEqualTo("abs");
        assertThat(saved.getTotalKcal()).isEqualTo(40 + 35 + 55);
        assertThat(saved.getItemsJson()).contains("\"捲腹\"").contains("\"登山者\"");

        assertThat(response.totalKcal()).isEqualTo(130);
        assertThat(response.items()).hasSize(3);
        assertThat(response.items().get(0).name()).isEqualTo("捲腹");
    }

    @Test
    void generate_appliesDefaults_whenDurationOrIntensityNull() {
        when(aiProvider.generateWorkout("legs", 30, "medium")).thenReturn(List.of(
                new ExerciseItem("深蹲", 4, "12", 60, 40, 70, "", List.of())));
        when(workouts.save(any(WorkoutPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.generate(owner, new GenerateWorkoutRequest(LocalDate.now(), WorkoutCategory.legs, null, null, null));

        verify(aiProvider).generateWorkout("legs", 30, "medium");
    }

    @Test
    void list_filtersByOwnerAndDate() {
        when(workouts.findByUserIdAndDateOrderByCreatedAtDesc(1L, LocalDate.of(2026, 5, 30)))
                .thenReturn(List.of());
        assertThat(service.list(owner, LocalDate.of(2026, 5, 30))).isEmpty();
    }

    @Test
    void get_returnsOwned_orThrows404() {
        WorkoutPlan plan = buildPlan(owner, 10L);
        when(workouts.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        assertThat(service.get(owner, 10L).id()).isEqualTo(10L);

        when(workouts.findByIdAndUserId(eq(11L), eq(1L))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(owner, 11L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ae.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });
    }

    @Test
    void complete_flipsDoneAndRecordsActualBurn() {
        WorkoutPlan plan = buildPlan(owner, 5L);
        plan.setTotalKcal(100);
        assertThat(plan.isDone()).isFalse();
        when(workouts.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(plan));
        when(workouts.save(plan)).thenAnswer(inv -> inv.getArgument(0));

        WorkoutPlanResponse response = service.complete(owner, 5L, 60);

        assertThat(plan.isDone()).isTrue();
        assertThat(response.done()).isTrue();
        assertThat(response.burnedKcal()).isEqualTo(60);
    }

    @Test
    void complete_clampsActualBurnToPlanTotal() {
        WorkoutPlan plan = buildPlan(owner, 6L);
        plan.setTotalKcal(80);
        when(workouts.findByIdAndUserId(6L, 1L)).thenReturn(Optional.of(plan));
        when(workouts.save(plan)).thenAnswer(inv -> inv.getArgument(0));

        // A tampered request can't claim more burn than the plan was worth.
        assertThat(service.complete(owner, 6L, 9999).burnedKcal()).isEqualTo(80);
        // A null body means "did the whole plan".
        assertThat(service.complete(owner, 6L, null).burnedKcal()).isEqualTo(80);
    }

    @Test
    void removeItems_dropsSelectedAndRecomputesTotal() {
        WorkoutPlan plan = buildPlan(owner, 8L);
        plan.setItemsJson(objectMapper.valueToTree(List.of(
                new ExerciseItem("捲腹", 4, "15", 45, 45, 40, "n", List.of()),
                new ExerciseItem("棒式", 3, "45s", 45, 45, 35, "n", List.of()),
                new ExerciseItem("登山者", 3, "30s", 60, 30, 55, "n", List.of()))).toString());
        plan.setTotalKcal(130);
        when(workouts.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(plan));
        when(workouts.save(plan)).thenAnswer(inv -> inv.getArgument(0));

        WorkoutPlanResponse response = service.removeItems(owner, 8L, List.of(1));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(ExerciseItem::name).containsExactly("捲腹", "登山者");
        assertThat(response.totalKcal()).isEqualTo(95);  // 40 + 55, 棒式 dropped
    }

    @Test
    void removeItems_deletesWholePlan_whenAllExercisesRemoved() {
        WorkoutPlan plan = buildPlan(owner, 11L);
        plan.setItemsJson(objectMapper.valueToTree(List.of(
                new ExerciseItem("捲腹", 4, "15", 45, 45, 40, "n", List.of()),
                new ExerciseItem("棒式", 3, "45s", 45, 45, 35, "n", List.of()))).toString());
        plan.setTotalKcal(75);
        when(workouts.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(plan));

        WorkoutPlanResponse response = service.removeItems(owner, 11L, List.of(0, 1));

        verify(workouts).delete(plan);
        verify(workouts, never()).save(any());
        assertThat(response.items()).isEmpty();
    }

    @Test
    void removeItems_throws409_whenPlanAlreadyDone() {
        WorkoutPlan plan = buildPlan(owner, 9L);
        plan.setDone(true);
        when(workouts.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.removeItems(owner, 9L, List.of(0)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).status())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(workouts, never()).save(any());
    }

    @Test
    void removeItems_throws400_whenNoIndexMatches() {
        WorkoutPlan plan = buildPlan(owner, 10L);
        plan.setItemsJson(objectMapper.valueToTree(List.of(
                new ExerciseItem("捲腹", 4, "15", 45, 45, 40, "n", List.of()))).toString());
        when(workouts.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.removeItems(owner, 10L, List.of(5)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(workouts, never()).save(any());
    }

    @Test
    void delete_throws_whenNotOwned() {
        when(workouts.findByIdAndUserId(42L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(other, 42L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(workouts, never()).delete(any());
    }

    @Test
    void delete_removesOwnedPlan() {
        WorkoutPlan plan = buildPlan(owner, 7L);
        when(workouts.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(plan));

        service.delete(owner, 7L);

        verify(workouts).delete(plan);
    }

    private WorkoutPlan buildPlan(AppUser user, long id) {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setUser(user);
        plan.setDate(LocalDate.now());
        plan.setCategory("abs");
        plan.setItemsJson("[]");
        plan.setTotalKcal(0);
        try {
            Field f = WorkoutPlan.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(plan, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return plan;
    }
}
