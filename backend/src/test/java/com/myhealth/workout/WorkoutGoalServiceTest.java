package com.myhealth.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutGoalDtos.SetWorkoutGoalRequest;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalResponse;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutGoalServiceTest {

    @Mock WorkoutGoalRepository goals;
    @Mock WorkoutPlanRepository workouts;

    WorkoutGoalService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new WorkoutGoalService(goals, workouts);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
        lenient().when(goals.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void get_returnsNullProgress_whenNoGoalSet() {
        when(goals.findByUserId(1L)).thenReturn(Optional.empty());

        WorkoutGoalResponse res = service.get(user);

        assertThat(res.progress()).isNull();
    }

    @Test
    void get_computesProgress_againstThisWeeksDoneWorkouts() {
        when(goals.findByUserId(1L)).thenReturn(Optional.of(new WorkoutGoal(user, 4)));
        when(workouts.countByUserIdAndDateBetweenAndDoneTrue(eq(1L), any(), any())).thenReturn(2);

        WorkoutGoalResponse res = service.get(user);

        assertThat(res.progress()).isNotNull();
        assertThat(res.progress().targetSessionsPerWeek()).isEqualTo(4);
        assertThat(res.progress().completedThisWeek()).isEqualTo(2);
        assertThat(res.progress().remaining()).isEqualTo(2);
        assertThat(res.progress().progressPct()).isEqualTo(50);
        assertThat(res.progress().achieved()).isFalse();
        assertThat(res.progress().weekStart().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    }

    @Test
    void get_clampsProgress_andMarksAchieved_whenTargetReachedOrExceeded() {
        when(goals.findByUserId(1L)).thenReturn(Optional.of(new WorkoutGoal(user, 4)));
        when(workouts.countByUserIdAndDateBetweenAndDoneTrue(eq(1L), any(), any())).thenReturn(5);

        WorkoutGoalResponse res = service.get(user);

        assertThat(res.progress().progressPct()).isEqualTo(100);  // clamped from 125
        assertThat(res.progress().remaining()).isZero();
        assertThat(res.progress().achieved()).isTrue();
    }

    @Test
    void set_createsGoal_whenNoneExists() {
        when(goals.findByUserId(1L)).thenReturn(Optional.empty());
        when(workouts.countByUserIdAndDateBetweenAndDoneTrue(eq(1L), any(), any())).thenReturn(0);

        WorkoutGoalResponse res = service.set(user, new SetWorkoutGoalRequest(3));

        ArgumentCaptor<WorkoutGoal> saved = ArgumentCaptor.forClass(WorkoutGoal.class);
        verify(goals).save(saved.capture());
        assertThat(saved.getValue().getTargetSessionsPerWeek()).isEqualTo(3);
        assertThat(res.progress().targetSessionsPerWeek()).isEqualTo(3);
    }

    @Test
    void set_updatesExistingGoal_inPlace() {
        WorkoutGoal existing = new WorkoutGoal(user, 2);
        when(goals.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(workouts.countByUserIdAndDateBetweenAndDoneTrue(eq(1L), any(), any())).thenReturn(1);

        WorkoutGoalResponse res = service.set(user, new SetWorkoutGoalRequest(5));

        assertThat(existing.getTargetSessionsPerWeek()).isEqualTo(5);
        assertThat(res.progress().targetSessionsPerWeek()).isEqualTo(5);
        verify(goals).save(existing);
    }

    @Test
    void delete_removesGoal_whenPresent() {
        WorkoutGoal existing = new WorkoutGoal(user, 3);
        when(goals.findByUserId(1L)).thenReturn(Optional.of(existing));

        service.delete(user);

        verify(goals).delete(existing);
    }

    @Test
    void streakWeeks_countsConsecutiveOnTargetWeeks_withCurrentWeekGrace() {
        LocalDate thisMon = LocalDate.of(2026, 6, 8);   // a Monday
        LocalDate lastMon = thisMon.minusWeeks(1);
        LocalDate twoAgo = thisMon.minusWeeks(2);
        // this week: 2 sessions, last week: 2, two weeks ago: 1.
        List<LocalDate> done = List.of(
                thisMon, thisMon.plusDays(2),
                lastMon, lastMon.plusDays(3),
                twoAgo);

        // target 2: this+last meet it, two-weeks-ago (1) breaks → streak 2.
        assertThat(WorkoutGoalService.streakWeeks(done, thisMon, 2)).isEqualTo(2);
        // no goal → 0.
        assertThat(WorkoutGoalService.streakWeeks(done, thisMon, 0)).isZero();
    }

    @Test
    void streakWeeks_graceWhenCurrentWeekNotYetMet() {
        LocalDate thisMon = LocalDate.of(2026, 6, 8);
        LocalDate lastMon = thisMon.minusWeeks(1);
        LocalDate twoAgo = thisMon.minusWeeks(2);
        // this week: only 1 session (below target 2), but prior two weeks met it.
        List<LocalDate> done = List.of(
                thisMon,
                lastMon, lastMon.plusDays(2),
                twoAgo, twoAgo.plusDays(2));

        // current week not met yet → counts back from last week → streak 2 (not 0).
        assertThat(WorkoutGoalService.streakWeeks(done, thisMon, 2)).isEqualTo(2);
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
