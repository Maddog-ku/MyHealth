package com.myhealth.healthplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanResponse;
import com.myhealth.healthplan.HealthPlanDtos.HealthPlanSettingsResponse;
import com.myhealth.healthplan.HealthPlanDtos.NutritionPlan;
import com.myhealth.healthplan.HealthPlanDtos.PlanAction;
import com.myhealth.healthplan.HealthPlanDtos.StreakPlan;
import com.myhealth.healthplan.HealthPlanDtos.WeightPlan;
import com.myhealth.healthplan.HealthPlanDtos.WorkoutPlanSummary;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.user.AppUser;
import com.myhealth.user.Goal;
import com.myhealth.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthPlanController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HealthPlanControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean CurrentUser currentUser;
    @MockBean HealthPlanService healthPlan;

    @Test
    void get_returnsPlanForRequestedDate() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        LocalDate date = LocalDate.of(2026, 6, 8);
        when(currentUser.require()).thenReturn(user);
        when(healthPlan.get(eq(user), eq(date))).thenReturn(sample(date));

        mockMvc.perform(get("/api/v1/health-plan").param("date", "2026-06-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-06-08"))
                .andExpect(jsonPath("$.primaryGoal").value("減脂"))
                .andExpect(jsonPath("$.nutrition.goalKcal").value(1700))
                .andExpect(jsonPath("$.nextActions[0].type").value("PLAN_WORKOUT"));
    }

    @Test
    void today_returnsPlanForCurrentDate() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        when(currentUser.require()).thenReturn(user);
        when(healthPlan.get(eq(user), any())).thenAnswer(invocation -> sample(invocation.getArgument(1)));

        mockMvc.perform(get("/api/v1/health-plan/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryGoal").value("減脂"));
    }

    @Test
    void settings_returnsIntegratedGoalSettings() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        when(currentUser.require()).thenReturn(user);
        when(healthPlan.settings(user)).thenReturn(new HealthPlanSettingsResponse(
                Goal.fat_loss, new BigDecimal("76.0"), null, null));

        mockMvc.perform(get("/api/v1/health-plan/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryGoal").value("fat_loss"))
                .andExpect(jsonPath("$.currentWeightKg").value(76.0));
    }

    @Test
    void updateSettings_acceptsIntegratedGoalPayload() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        when(currentUser.require()).thenReturn(user);
        when(healthPlan.updateSettings(eq(user), any())).thenReturn(new HealthPlanSettingsResponse(
                Goal.muscle_gain, new BigDecimal("76.0"), null, null));

        mockMvc.perform(put("/api/v1/health-plan/settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "primaryGoal": "muscle_gain",
                                  "weightGoal": { "enabled": true, "targetWeightKg": 80.0, "targetDate": "2026-09-01" },
                                  "workoutGoal": { "enabled": true, "targetSessionsPerWeek": 4 }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryGoal").value("muscle_gain"));
    }

    private HealthPlanResponse sample(LocalDate date) {
        return new HealthPlanResponse(
                date,
                "減脂",
                78,
                new NutritionPlan(1700, 1900, 900, 200, 1000, 47, false,
                        List.of(new MacroBudget("protein", 149, 80, 54))),
                new WeightPlan(true, new BigDecimal("76.0"), new BigDecimal("70.0"),
                        new BigDecimal("-6.0"), 25, null, null, true, false),
                new WorkoutPlanSummary(true, 1, 1, 4, 2, 2, 50, false),
                new StreakPlan(3, 8, date),
                List.of(new PlanAction("PLAN_WORKOUT", "安排下一次訓練", "本週還差 2 次訓練", 70, "/workouts")));
    }
}
