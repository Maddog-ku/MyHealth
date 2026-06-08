package com.myhealth.goal;

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
import com.myhealth.goal.GoalDtos.WeightGoalProgress;
import com.myhealth.goal.GoalDtos.WeightGoalResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GoalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GoalControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean GoalService goalService;
    @MockBean CurrentUser currentUser;

    private AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    private WeightGoalResponse sample() {
        WeightGoalProgress p = new WeightGoalProgress(
                new BigDecimal("70.0"), new BigDecimal("80.0"), new BigDecimal("76.0"),
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 7, 6),
                new BigDecimal("-6.0"), new BigDecimal("-4.0"), 40, -2.0, -2.8,
                LocalDate.of(2026, 6, 27), true, false, Instant.parse("2026-05-23T00:00:00Z"));
        return new WeightGoalResponse(p);
    }

    @Test
    void get_returns200_withProgress() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(goalService.get(any())).thenReturn(sample());

        mockMvc.perform(get("/api/v1/weight-goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.targetWeightKg").value(70.0))
                .andExpect(jsonPath("$.progress.progressPct").value(40))
                .andExpect(jsonPath("$.progress.onTrack").value(true));
    }

    @Test
    void get_returns200_withNullProgress_whenNoGoal() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(goalService.get(any())).thenReturn(new WeightGoalResponse(null));

        mockMvc.perform(get("/api/v1/weight-goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @Test
    void put_returns200_andSetsGoal() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);
        when(goalService.set(any(), any())).thenReturn(sample());

        mockMvc.perform(put("/api/v1/weight-goal")
                        .contentType("application/json")
                        .content("{\"targetWeightKg\":70.0,\"targetDate\":\"2026-07-06\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.targetWeightKg").value(70.0));

        verify(goalService).set(any(), any());
    }

    @Test
    void put_returns400_whenTargetWeightMissing() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());

        mockMvc.perform(put("/api/v1/weight-goal")
                        .contentType("application/json")
                        .content("{\"targetDate\":\"2026-07-06\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/weight-goal"))
                .andExpect(status().isNoContent());

        verify(goalService).delete(user);
    }
}
