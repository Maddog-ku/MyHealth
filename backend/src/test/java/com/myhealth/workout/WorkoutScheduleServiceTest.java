package com.myhealth.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ScheduleDay;
import com.myhealth.common.ApiException;
import com.myhealth.user.AppUser;
import com.myhealth.user.Goal;
import com.myhealth.user.Profile;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import com.myhealth.workout.WorkoutScheduleDtos.ApplyDayRequest;
import com.myhealth.workout.WorkoutScheduleDtos.GeneratePlanRequest;
import com.myhealth.workout.WorkoutScheduleDtos.WorkoutScheduleResponse;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutScheduleServiceTest {

    @Mock WorkoutScheduleRepository schedules;
    @Mock WorkoutService workoutService;
    @Mock AiProvider aiProvider;

    final ObjectMapper objectMapper = new ObjectMapper();
    TransactionTemplate transactionTemplate;
    WorkoutScheduleService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        service = new WorkoutScheduleService(schedules, workoutService, aiProvider, objectMapper, transactionTemplate);

        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
        Profile p = new Profile();
        p.setGoal(Goal.muscle_gain);
        user.setProfile(p);

        lenient().when(schedules.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<ScheduleDay> threeDaySplit() {
        return List.of(
                new ScheduleDay(1, false, "legs", 40, "下肢肌力"),
                ScheduleDay.restDay(2),
                new ScheduleDay(3, false, "chest", 35, "胸與三頭"),
                ScheduleDay.restDay(4),
                new ScheduleDay(5, false, "back", 35, "背與二頭"),
                ScheduleDay.restDay(6),
                ScheduleDay.restDay(7));
    }

    @Test
    void generate_snapsStartDateToMonday_passesGoalLabel_andPersistsPattern() {
        when(aiProvider.planWorkoutSchedule(eq("增肌"), eq(3), eq("medium"))).thenReturn(threeDaySplit());
        // 2026-06-03 is a Wednesday → schedule should start on Monday 2026-06-01.
        GeneratePlanRequest req = new GeneratePlanRequest(LocalDate.of(2026, 6, 3), 3, 2, WorkoutIntensity.medium);

        WorkoutScheduleResponse res = service.generate(user, req);

        assertThat(res.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(res.goal()).isEqualTo("增肌");
        assertThat(res.weeks()).isEqualTo(2);
        assertThat(res.daysPerWeek()).isEqualTo(3);
        assertThat(res.intensity()).isEqualTo("medium");
        assertThat(res.days()).hasSize(7);
        assertThat(res.days().stream().filter(d -> !d.rest()).count()).isEqualTo(3);
        assertThat(res.days().get(0).category()).isEqualTo("legs");

        ArgumentCaptor<WorkoutSchedule> saved = ArgumentCaptor.forClass(WorkoutSchedule.class);
        verify(schedules).save(saved.capture());
        assertThat(saved.getValue().getDaysJson()).contains("\"legs\"").contains("\"chest\"");
    }

    @Test
    void generate_defaultsIntensityToMedium_whenNull() {
        when(aiProvider.planWorkoutSchedule(any(), eq(3), eq("medium"))).thenReturn(threeDaySplit());
        GeneratePlanRequest req = new GeneratePlanRequest(LocalDate.of(2026, 6, 1), 3, 1, null);

        WorkoutScheduleResponse res = service.generate(user, req);

        assertThat(res.intensity()).isEqualTo("medium");
        verify(aiProvider).planWorkoutSchedule(any(), eq(3), eq("medium"));
    }

    @Test
    void applyDay_generatesWorkout_forTrainingDay_atScheduleIntensity() {
        WorkoutSchedule schedule = persisted(threeDaySplit(), WorkoutIntensity.high);
        when(schedules.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(schedule));
        WorkoutPlanResponse plan = new WorkoutPlanResponse(99L, LocalDate.of(2026, 6, 1), "legs",
                List.of(), 200, null, false, Instant.now());
        when(workoutService.generate(eq(user), any())).thenReturn(plan);

        // 2026-06-01 is a Monday (weekday 1) → legs training day.
        WorkoutPlanResponse res = service.applyDay(user, 10L,
                new ApplyDayRequest(LocalDate.of(2026, 6, 1), 1));

        assertThat(res.id()).isEqualTo(99L);
        ArgumentCaptor<GenerateWorkoutRequest> gen = ArgumentCaptor.forClass(GenerateWorkoutRequest.class);
        verify(workoutService).generate(eq(user), gen.capture());
        assertThat(gen.getValue().date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(gen.getValue().category()).isEqualTo(WorkoutCategory.legs);
        assertThat(gen.getValue().durationMin()).isEqualTo(40);
        assertThat(gen.getValue().intensity()).isEqualTo(WorkoutIntensity.high);  // schedule's intensity
    }

    @Test
    void applyDay_rejectsRestDay_with400() {
        WorkoutSchedule schedule = persisted(threeDaySplit(), WorkoutIntensity.medium);
        when(schedules.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(schedule));

        // 2026-06-02 is a Tuesday (weekday 2) → a rest day in the split.
        assertThatThrownBy(() -> service.applyDay(user, 10L,
                new ApplyDayRequest(LocalDate.of(2026, 6, 2), 2)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rest day");
    }

    @Test
    void applyDay_rejectsWhenDateWeekdayDoesNotMatch_with400() {
        WorkoutSchedule schedule = persisted(threeDaySplit(), WorkoutIntensity.medium);
        when(schedules.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(schedule));

        // weekday=1 (Monday) but the date 2026-06-03 is a Wednesday → mismatch.
        assertThatThrownBy(() -> service.applyDay(user, 10L,
                new ApplyDayRequest(LocalDate.of(2026, 6, 3), 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("weekday");
    }

    @Test
    void get_throwsNotFound_whenMissing() {
        when(schedules.findByIdAndUserId(404L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(user, 404L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void delete_removesOwnedSchedule() {
        WorkoutSchedule schedule = persisted(threeDaySplit(), WorkoutIntensity.medium);
        when(schedules.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(schedule));

        service.delete(user, 10L);

        verify(schedules).delete(schedule);
    }

    private WorkoutSchedule persisted(List<ScheduleDay> days, WorkoutIntensity intensity) {
        WorkoutSchedule s = new WorkoutSchedule();
        s.setUser(user);
        s.setGoal("增肌");
        s.setStartDate(LocalDate.of(2026, 6, 1));
        s.setWeeks(2);
        s.setDaysPerWeek((int) days.stream().filter(d -> !d.rest()).count());
        s.setIntensity(intensity.name());
        try {
            s.setDaysJson(objectMapper.writeValueAsString(days));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return s;
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
