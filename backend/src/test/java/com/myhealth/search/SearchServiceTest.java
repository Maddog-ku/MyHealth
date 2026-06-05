package com.myhealth.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.myhealth.meal.Meal;
import com.myhealth.meal.MealRepository;
import com.myhealth.search.SearchDtos.SearchResponse;
import com.myhealth.search.SearchDtos.SearchResult;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutPlan;
import com.myhealth.workout.WorkoutPlanRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock MealRepository meals;
    @Mock WorkoutPlanRepository workouts;

    SearchService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new SearchService(meals, workouts);
        user = new AppUser();
        user.setEmail("a@b.c");
        user.setRole(Role.USER);
        setId(user, "id", 1L);
    }

    @Test
    void search_blankQuery_returnsEmpty_withoutHittingRepos() {
        SearchResponse res = service.search(user, "   ", null);

        assertThat(res.query()).isEmpty();
        assertThat(res.results()).isEmpty();
        verifyNoInteractions(meals, workouts);
    }

    @Test
    void search_mergesLocalizesAndSortsByDateDesc() {
        when(meals.search(eq(1L), any(), any())).thenReturn(List.of(
                meal(11L, LocalDate.of(2026, 6, 5), "雞胸肉沙拉", "lunch", 420)));
        when(workouts.search(eq(1L), any(), any())).thenReturn(List.of(
                workout(22L, LocalDate.of(2026, 6, 6), "abs", true, 120)));

        SearchResponse res = service.search(user, "雞", null);

        assertThat(res.results()).extracting(SearchResult::type, SearchResult::title, SearchResult::subtitle)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("WORKOUT", "腹肌核心", "已完成"),  // 6/6 first
                        org.assertj.core.groups.Tuple.tuple("MEAL", "雞胸肉沙拉", "午餐"));     // 6/5 second
    }

    @Test
    void search_passesLowercasedLikePattern() {
        when(meals.search(any(), any(), any())).thenReturn(List.of());
        when(workouts.search(any(), any(), any())).thenReturn(List.of());

        service.search(user, "Chicken", null);

        ArgumentCaptor<String> like = ArgumentCaptor.forClass(String.class);
        verify(meals).search(eq(1L), like.capture(), any());
        assertThat(like.getValue()).isEqualTo("%chicken%");
    }

    @Test
    void search_capsTotalResults() {
        when(meals.search(eq(1L), any(), any())).thenReturn(List.of(
                meal(11L, LocalDate.of(2026, 6, 5), "沙拉", "lunch", 420)));
        when(workouts.search(eq(1L), any(), any())).thenReturn(List.of(
                workout(22L, LocalDate.of(2026, 6, 6), "abs", true, 120)));

        SearchResponse res = service.search(user, "x", 1); // limit 1 → only the most recent

        assertThat(res.results()).hasSize(1);
        assertThat(res.results().get(0).type()).isEqualTo("WORKOUT");
    }

    @Test
    void search_blankMealDescription_fallsBackToSlotLabel() {
        when(meals.search(eq(1L), any(), any())).thenReturn(List.of(
                meal(11L, LocalDate.of(2026, 6, 5), "  ", "breakfast", 300)));
        when(workouts.search(eq(1L), any(), any())).thenReturn(List.of());

        SearchResponse res = service.search(user, "x", null);

        assertThat(res.results().get(0).title()).isEqualTo("早餐");
    }

    private Meal meal(Long id, LocalDate date, String desc, String slot, int kcal) {
        Meal m = new Meal();
        setId(m, "id", id);
        m.setDate(date);
        m.setDescription(desc);
        m.setSlot(slot);
        m.setTotalKcal(kcal);
        return m;
    }

    private WorkoutPlan workout(Long id, LocalDate date, String category, boolean done, int kcal) {
        WorkoutPlan w = new WorkoutPlan();
        setId(w, "id", id);
        w.setDate(date);
        w.setCategory(category);
        w.setDone(done);
        w.setTotalKcal(kcal);
        return w;
    }

    private static void setId(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
