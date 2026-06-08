package com.myhealth.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.report.ReportDtos.WeeklyAdherence;
import com.myhealth.report.ReportDtos.WeeklyReportResponse;
import com.myhealth.report.ReportDtos.WeeklySummary;
import com.myhealth.report.ReportDtos.WeeklyTrend;
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

@WebMvcTest(controllers = ReportController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ReportService reportService;
    @MockBean CurrentUser currentUser;
    @MockBean AiEndpointRateLimiter rateLimiter;

    private AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    private WeeklyReportResponse sample(String narrative) {
        WeeklySummary summary = new WeeklySummary(4200, 600, 800, 3400, 1700,
                new BigDecimal("70.0"), new BigDecimal("69.4"), new BigDecimal("-0.6"), 3, 14, 7);
        WeeklyAdherence adherence = new WeeklyAdherence(86, 92, 75, 4, 100, 7, 7);
        var trends = java.util.List.of(
                new WeeklyTrend("PROTEIN_LOW", "warn", "蛋白質偏低", "本週蛋白質達標率約 60%…"));
        return new WeeklyReportResponse(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7),
                summary, adherence, trends, narrative,
                narrative == null ? null : Instant.parse("2026-06-05T10:00:00Z"));
    }

    @Test
    void weekly_returns200_withSummaryAndNarrative() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(reportService.get(any(), eq(LocalDate.of(2026, 6, 3)))).thenReturn(sample("這週很棒 💪"));

        mockMvc.perform(get("/api/v1/reports/weekly").param("weekStart", "2026-06-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-06-01"))
                .andExpect(jsonPath("$.summary.totalIntakeKcal").value(4200))
                .andExpect(jsonPath("$.summary.workoutsDone").value(3))
                .andExpect(jsonPath("$.adherence.caloriePct").value(86))
                .andExpect(jsonPath("$.adherence.workoutTarget").value(4))
                .andExpect(jsonPath("$.trends[0].type").value("PROTEIN_LOW"))
                .andExpect(jsonPath("$.narrative").value("這週很棒 💪"));
    }

    @Test
    void weekly_returns200_withNullNarrative_whenNotGenerated() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(reportService.get(any(), isNull())).thenReturn(sample(null));

        mockMvc.perform(get("/api/v1/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative").doesNotExist())
                .andExpect(jsonPath("$.summary.mealsLogged").value(14));
    }

    @Test
    void generate_returns200_andEnforcesRateLimit() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);
        when(reportService.generate(any(), eq(LocalDate.of(2026, 6, 2)))).thenReturn(sample("回顧內容"));

        mockMvc.perform(post("/api/v1/reports/weekly/generate")
                        .contentType("application/json")
                        .content("{\"weekStart\":\"2026-06-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative").value("回顧內容"));

        verify(rateLimiter).checkReport(user);
        verify(reportService).generate(user, LocalDate.of(2026, 6, 2));
    }

    @Test
    void generate_defaultsToCurrentWeek_whenBodyOmitted() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);
        when(reportService.generate(any(), isNull())).thenReturn(sample("本週回顧"));

        mockMvc.perform(post("/api/v1/reports/weekly/generate"))
                .andExpect(status().isOk());

        verify(rateLimiter).checkReport(user);
        verify(reportService).generate(user, null);
    }
}
