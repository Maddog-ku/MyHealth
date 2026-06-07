package com.myhealth.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutVolumeDtos.VolumeResponse;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutVolumeServiceTest {

    @Mock WorkoutPlanRepository workouts;

    final ObjectMapper objectMapper = new ObjectMapper();
    WorkoutVolumeService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new WorkoutVolumeService(workouts, objectMapper);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, 1L);
    }

    @Test
    void volume_aggregatesCompletedWorkouts_byTotalsCategoriesAndWeeks() {
        LocalDate today = LocalDate.now();
        // Two legs sessions today (same day) + one chest session a week ago; one unfinished
        // plan that must be ignored.
        WorkoutPlan a = plan(today, "legs", new int[]{4, 3}, 200, 220, true);          // 7 sets, burned 200
        WorkoutPlan c = plan(today, "legs", new int[]{4, 3}, null, 180, true);          // 7 sets, effective 180
        WorkoutPlan b = plan(today.minusWeeks(1), "chest", new int[]{3, 2}, 150, 150, true); // 5 sets, 150
        WorkoutPlan notDone = plan(today, "back", new int[]{5}, null, 90, false);       // excluded
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any()))
                .thenReturn(List.of(a, c, b, notDone));

        VolumeResponse res = service.volume(user, 4);

        assertThat(res.weeks()).isEqualTo(4);
        assertThat(res.totalSessions()).isEqualTo(3);
        assertThat(res.totalSets()).isEqualTo(19);          // 7 + 7 + 5
        assertThat(res.totalKcal()).isEqualTo(530);         // 200 + 180 + 150
        assertThat(res.activeDays()).isEqualTo(2);          // today (a, c) + last week (b)
        assertThat(res.avgSessionsPerWeek()).isEqualTo(0.8); // 3 / 4 weeks

        // Categories sorted by sessions desc: legs (2) before chest (1).
        assertThat(res.byCategory()).hasSize(2);
        assertThat(res.byCategory().get(0).category()).isEqualTo("legs");
        assertThat(res.byCategory().get(0).sessions()).isEqualTo(2);
        assertThat(res.byCategory().get(0).sets()).isEqualTo(14);
        assertThat(res.byCategory().get(1).category()).isEqualTo("chest");

        // Continuous 4-week series; this week holds the two legs sessions, the prior week one.
        assertThat(res.series()).hasSize(4);
        assertThat(res.series().get(3).sessions()).isEqualTo(2);  // current week (last bucket)
        assertThat(res.series().get(2).sessions()).isEqualTo(1);  // a week ago
        assertThat(res.series().get(0).sessions()).isEqualTo(0);  // empty earlier week still present
    }

    @Test
    void volume_clampsWeeks_toAllowedRange() {
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any())).thenReturn(List.of());

        assertThat(service.volume(user, 99).weeks()).isEqualTo(WorkoutVolumeService.MAX_WEEKS);
        assertThat(service.volume(user, 0).weeks()).isEqualTo(WorkoutVolumeService.MIN_WEEKS);
        assertThat(service.volume(user, null).weeks()).isEqualTo(4);  // default
    }

    @Test
    void volume_returnsEmptyShape_whenNoCompletedWorkouts() {
        when(workouts.findByUserIdAndDateBetweenOrderByDateAsc(eq(1L), any(), any())).thenReturn(List.of());

        VolumeResponse res = service.volume(user, 6);

        assertThat(res.totalSessions()).isZero();
        assertThat(res.byCategory()).isEmpty();
        assertThat(res.series()).hasSize(6);              // buckets still present for the chart
        assertThat(res.series().stream().allMatch(w -> w.sessions() == 0)).isTrue();
    }

    private WorkoutPlan plan(LocalDate date, String category, int[] setsPerItem,
                             Integer burnedKcal, int totalKcal, boolean done) {
        WorkoutPlan p = new WorkoutPlan();
        p.setUser(user);
        p.setDate(date);
        p.setCategory(category);
        p.setTotalKcal(totalKcal);
        p.setBurnedKcal(burnedKcal);
        p.setDone(done);
        List<ExerciseItem> items = new ArrayList<>();
        for (int sets : setsPerItem) {
            items.add(new ExerciseItem("動作", sets, "10", 45, 40, 30, "要點", List.of()));
        }
        try {
            p.setItemsJson(objectMapper.writeValueAsString(items));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return p;
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
