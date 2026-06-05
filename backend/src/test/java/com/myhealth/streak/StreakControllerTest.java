package com.myhealth.streak;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.streak.StreakDtos.AchievementView;
import com.myhealth.streak.StreakDtos.StreakInfo;
import com.myhealth.streak.StreakDtos.StreakSummaryResponse;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StreakController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StreakControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StreakService streakService;
    @MockBean CurrentUser currentUser;

    private AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void streak_returns200_withStreaksAchievementsAndNewlyUnlocked() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);
        AchievementView badge = new AchievementView("STREAK_3", "三日連勝", "🔥", "連續 3 天", 3, 3, true, null);
        when(streakService.getSummary(any())).thenReturn(new StreakSummaryResponse(
                new StreakInfo(3, 5, LocalDate.of(2026, 6, 6)),
                new StreakInfo(1, 2, LocalDate.of(2026, 6, 6)),
                new StreakInfo(3, 5, LocalDate.of(2026, 6, 6)),
                List.of(badge),
                List.of("STREAK_3")));

        mockMvc.perform(get("/api/v1/streak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealStreak.current").value(3))
                .andExpect(jsonPath("$.workoutStreak.longest").value(2))
                .andExpect(jsonPath("$.achievements[0].code").value("STREAK_3"))
                .andExpect(jsonPath("$.achievements[0].unlocked").value(true))
                .andExpect(jsonPath("$.newlyUnlocked[0]").value("STREAK_3"));

        verify(streakService).getSummary(user);
    }
}
