package com.myhealth.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.stats.StatsDtos.CalorieBudgetResponse;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import com.myhealth.stats.StatsDtos.SeriesPoint;
import com.myhealth.user.AppUser;
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

@WebMvcTest(controllers = StatsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StatsControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StatsService statsService;
    @MockBean CurrentUser currentUser;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void daily_returns200_withStatsFromService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(statsService.daily(any(), eq(LocalDate.of(2026, 5, 30)))).thenReturn(new DailyStatsResponse(
                LocalDate.of(2026, 5, 30), 720, 0, 720,
                new BigDecimal("56.0"), new BigDecimal("28.0"), new BigDecimal("84.0"),
                new BigDecimal("70.0"), 1700, 0, 2));

        mockMvc.perform(get("/api/v1/stats/daily").param("date", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeKcal").value(720))
                .andExpect(jsonPath("$.netKcal").value(720))
                .andExpect(jsonPath("$.workoutsPlanned").value(2));
    }

    @Test
    void daily_returns400_whenDateMissing() throws Exception {
        mockMvc.perform(get("/api/v1/stats/daily"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void budget_returns200_withRingAndMacros() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(statsService.budget(any(), eq(LocalDate.of(2026, 5, 30)))).thenReturn(new CalorieBudgetResponse(
                LocalDate.of(2026, 5, 30), 1700, 1200, 300, 2000, 800, 60, false,
                List.of(new MacroBudget("protein", 128, 90, 70),
                        new MacroBudget("carb", 170, 120, 71),
                        new MacroBudget("fat", 57, 40, 70))));

        mockMvc.perform(get("/api/v1/stats/budget").param("date", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetKcal").value(2000))
                .andExpect(jsonPath("$.remainingKcal").value(800))
                .andExpect(jsonPath("$.over").value(false))
                .andExpect(jsonPath("$.macros[0].name").value("protein"))
                .andExpect(jsonPath("$.macros[0].targetG").value(128));
    }

    @Test
    void range_returns200_withSeries() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(statsService.range(any(), eq(LocalDate.of(2026, 5, 24)), eq(LocalDate.of(2026, 5, 30))))
                .thenReturn(new RangeStatsResponse(
                        LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 30),
                        List.of(
                                new SeriesPoint(LocalDate.of(2026, 5, 29), 0, 100, new BigDecimal("70"),
                                        new BigDecimal("20.0"), new BigDecimal("32.0"), new BigDecimal("80"), new BigDecimal("55.0")),
                                new SeriesPoint(LocalDate.of(2026, 5, 30), 720, 0, new BigDecimal("70"),
                                        new BigDecimal("20.0"), new BigDecimal("32.0"), new BigDecimal("80"), new BigDecimal("55.0")))));

        mockMvc.perform(get("/api/v1/stats/range")
                        .param("from", "2026-05-24")
                        .param("to", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-05-24"))
                .andExpect(jsonPath("$.to").value("2026-05-30"))
                .andExpect(jsonPath("$.series.length()").value(2))
                .andExpect(jsonPath("$.series[1].intakeKcal").value(720));
    }

    @Test
    void range_returns400_whenFromMissing() throws Exception {
        mockMvc.perform(get("/api/v1/stats/range").param("to", "2026-05-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("from"));
    }

    @Test
    void daily_returns400_whenDateMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/stats/daily").param("date", "30-May-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("date"));
    }
}
