package com.myhealth.habit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhealth.habit.HabitDtos.ToggleHabitRequest;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
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

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {
    @Mock HabitLogRepository logs;

    HabitService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new HabitService(logs);
        user = userWithId(1L);
    }

    @Test
    void daily_returnsBuiltInHabitsWithCompletedStatus() {
        HabitLog water = log(HabitType.WATER, LocalDate.of(2026, 6, 6));
        when(logs.findByUserIdAndDateBetween(eq(1L), any(), eq(LocalDate.of(2026, 6, 6)))).thenReturn(List.of(water));

        var response = service.daily(user, LocalDate.of(2026, 6, 6));

        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(response.completed()).isEqualTo(1);
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.items()).extracting("type")
                .containsExactly(HabitType.WATER, HabitType.STRETCH, HabitType.PROTEIN, HabitType.SLEEP);
        assertThat(response.items().getFirst().completed()).isTrue();
    }

    @Test
    void daily_computesPerHabitStreak_endingOnTargetDate() {
        LocalDate today = LocalDate.of(2026, 6, 6);
        // WATER done 3 consecutive days ending today; STRETCH done today only after a gap.
        when(logs.findByUserIdAndDateBetween(eq(1L), any(), eq(today))).thenReturn(List.of(
                log(HabitType.WATER, today),
                log(HabitType.WATER, today.minusDays(1)),
                log(HabitType.WATER, today.minusDays(2)),
                log(HabitType.STRETCH, today),
                log(HabitType.STRETCH, today.minusDays(3))));  // gap on day-1/2 → streak resets

        var response = service.daily(user, today);

        assertThat(streakOf(response, HabitType.WATER)).isEqualTo(3);
        assertThat(streakOf(response, HabitType.STRETCH)).isEqualTo(1);
        assertThat(streakOf(response, HabitType.PROTEIN)).isZero();  // never done
    }

    @Test
    void daily_streakGetsGraceDay_whenTodayNotYetDone() {
        LocalDate today = LocalDate.of(2026, 6, 6);
        // Done yesterday and the day before, nothing today yet → still a live 2-day streak.
        when(logs.findByUserIdAndDateBetween(eq(1L), any(), eq(today))).thenReturn(List.of(
                log(HabitType.SLEEP, today.minusDays(1)),
                log(HabitType.SLEEP, today.minusDays(2))));

        var response = service.daily(user, today);

        assertThat(streakOf(response, HabitType.SLEEP)).isEqualTo(2);
        assertThat(response.items().stream().filter(i -> i.type() == HabitType.SLEEP).findFirst().orElseThrow().completed())
                .isFalse();
    }

    private int streakOf(com.myhealth.habit.HabitDtos.DailyHabitsResponse response, HabitType type) {
        return response.items().stream().filter(i -> i.type() == type).findFirst().orElseThrow().streak();
    }

    @Test
    void toggle_completed_createsLog_whenMissing() {
        when(logs.findByUserIdAndDateAndType(1L, LocalDate.of(2026, 6, 6), HabitType.STRETCH))
                .thenReturn(Optional.empty());
        when(logs.save(any(HabitLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logs.findByUserIdAndDateBetween(eq(1L), any(), eq(LocalDate.of(2026, 6, 6))))
                .thenReturn(List.of(log(HabitType.STRETCH, LocalDate.of(2026, 6, 6))));

        var response = service.toggle(user, HabitType.STRETCH,
                new ToggleHabitRequest(LocalDate.of(2026, 6, 6), true));

        ArgumentCaptor<HabitLog> captor = ArgumentCaptor.forClass(HabitLog.class);
        verify(logs).save(captor.capture());
        HabitLog saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(saved.getType()).isEqualTo(HabitType.STRETCH);
        assertThat(response.completed()).isEqualTo(1);
    }

    @Test
    void toggle_completed_doesNotDuplicateExistingLog() {
        HabitLog existing = log(HabitType.WATER, LocalDate.of(2026, 6, 6));
        when(logs.findByUserIdAndDateAndType(1L, LocalDate.of(2026, 6, 6), HabitType.WATER))
                .thenReturn(Optional.of(existing));
        when(logs.findByUserIdAndDateBetween(eq(1L), any(), eq(LocalDate.of(2026, 6, 6)))).thenReturn(List.of(existing));

        service.toggle(user, HabitType.WATER, new ToggleHabitRequest(LocalDate.of(2026, 6, 6), true));

        verify(logs, never()).save(any());
    }

    @Test
    void toggle_incomplete_deletesExistingLog() {
        HabitLog existing = log(HabitType.PROTEIN, LocalDate.of(2026, 6, 6));
        when(logs.findByUserIdAndDateAndType(1L, LocalDate.of(2026, 6, 6), HabitType.PROTEIN))
                .thenReturn(Optional.of(existing));
        when(logs.findByUserIdAndDateBetween(eq(1L), any(), eq(LocalDate.of(2026, 6, 6)))).thenReturn(List.of());

        var response = service.toggle(user, HabitType.PROTEIN,
                new ToggleHabitRequest(LocalDate.of(2026, 6, 6), false));

        verify(logs).delete(existing);
        assertThat(response.completed()).isZero();
    }

    private HabitLog log(HabitType type, LocalDate date) {
        HabitLog log = new HabitLog();
        log.setUser(user);
        log.setDate(date);
        log.setType(type);
        return log;
    }

    private AppUser userWithId(long id) {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
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
}
