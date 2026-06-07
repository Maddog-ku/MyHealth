package com.myhealth.workout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalProgress;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalResponse;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WorkoutGoalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WorkoutGoalControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean WorkoutGoalService goalService;
    @MockBean CurrentUser currentUser;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void get_returnsProgress_whenGoalSet() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(goalService.get(any())).thenReturn(new WorkoutGoalResponse(new WorkoutGoalProgress(
                4, 2, 2, 50, LocalDate.of(2026, 6, 1), false, Instant.parse("2026-06-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/workout-goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.targetSessionsPerWeek").value(4))
                .andExpect(jsonPath("$.progress.completedThisWeek").value(2))
                .andExpect(jsonPath("$.progress.progressPct").value(50));
    }

    @Test
    void get_returnsNullProgress_whenNoGoal() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(goalService.get(any())).thenReturn(new WorkoutGoalResponse(null));

        mockMvc.perform(get("/api/v1/workout-goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @Test
    void set_returns200_andDelegates() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(goalService.set(any(), any())).thenReturn(new WorkoutGoalResponse(new WorkoutGoalProgress(
                3, 0, 3, 0, LocalDate.of(2026, 6, 1), false, Instant.parse("2026-06-01T00:00:00Z"))));

        mockMvc.perform(put("/api/v1/workout-goal")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetSessionsPerWeek\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.targetSessionsPerWeek").value(3));
    }

    @Test
    void set_returns400_whenTargetOutOfRange() throws Exception {
        mockMvc.perform(put("/api/v1/workout-goal")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetSessionsPerWeek\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("targetSessionsPerWeek")));
    }

    @Test
    void set_returns400_whenTargetMissing() throws Exception {
        mockMvc.perform(put("/api/v1/workout-goal")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void delete_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/workout-goal"))
                .andExpect(status().isNoContent());

        verify(goalService).delete(user);
    }
}
