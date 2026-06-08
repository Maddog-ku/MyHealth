package com.myhealth.habit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.habit.HabitDtos.DailyHabitsResponse;
import com.myhealth.habit.HabitDtos.HabitItemResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HabitController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HabitControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean HabitService habitService;
    @MockBean CurrentUser currentUser;

    @Test
    void daily_returns200_withHabitSummary() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(habitService.daily(any(), eq(LocalDate.of(2026, 6, 6)))).thenReturn(stubResponse());

        mockMvc.perform(get("/api/v1/habits/daily").param("date", "2026-06-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].type").value("WATER"))
                .andExpect(jsonPath("$.items[0].completed").value(true));
    }

    @Test
    void daily_returns400_whenDateMissing() throws Exception {
        mockMvc.perform(get("/api/v1/habits/daily"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void toggle_returns200_andDelegatesToService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(habitService.toggle(any(), eq(HabitType.WATER), any())).thenReturn(stubResponse());

        mockMvc.perform(post("/api/v1/habits/WATER/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-06\",\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(1));
    }

    @Test
    void toggle_returns400_whenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/habits/WATER/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("date")));
    }

    private DailyHabitsResponse stubResponse() {
        return new DailyHabitsResponse(LocalDate.of(2026, 6, 6), 1, 4, List.of(
                new HabitItemResponse(HabitType.WATER, "喝水", "今天至少補足 6 杯水", true,
                        java.time.Instant.parse("2026-06-06T00:00:00Z"), 3),
                new HabitItemResponse(HabitType.STRETCH, "伸展", "完成 5 分鐘伸展或活動度練習", false, null, 0)
        ));
    }

    private AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }
}
