package com.myhealth.food;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.food.FoodDtos.FoodResponse;
import com.myhealth.food.FoodDtos.FoodSuggestion;
import com.myhealth.food.FoodDtos.FoodSuggestionsResponse;
import com.myhealth.stats.StatsDtos.CalorieBudgetResponse;
import com.myhealth.stats.StatsDtos.MacroBudget;
import com.myhealth.stats.StatsService;
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

@WebMvcTest(controllers = FoodController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FoodControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean FoodService foodService;
    @MockBean StatsService stats;
    @MockBean CurrentUser currentUser;

    @Test
    void search_returnsFoods() throws Exception {
        when(foodService.search(eq("雞"), eq(5))).thenReturn(List.of(
                new FoodResponse("chicken-breast", "雞胸肉", "蛋白質", 150, 248,
                        new BigDecimal("46.50"), new BigDecimal("5.40"), BigDecimal.ZERO, List.of("雞肉"))));

        mockMvc.perform(get("/api/v1/foods").param("q", "雞").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("chicken-breast"))
                .andExpect(jsonPath("$[0].name").value("雞胸肉"))
                .andExpect(jsonPath("$[0].kcal").value(248));
    }

    @Test
    void suggestions_derivesGapFromBudget_andReturnsPicks() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("u@example.com");
        user.setRole(Role.USER);
        when(currentUser.require()).thenReturn(user);
        // Protein target 100g, consumed 40g → gap 60g; 500 kcal remaining; not over.
        when(stats.budget(eq(user), eq(LocalDate.now()))).thenReturn(new CalorieBudgetResponse(
                LocalDate.now(), 1700, 1200, 0, 1700, 500, 70, false,
                List.of(new MacroBudget("protein", 100, 40, 40))));
        when(foodService.suggest(eq(500), eq(60), eq(false))).thenReturn(new FoodSuggestionsResponse(
                500, 60, false, "下一餐優先補蛋白質（缺口約 60g）",
                List.of(new FoodSuggestion("chicken-breast", "雞胸肉", "蛋白質", 150, 248,
                        new BigDecimal("46.50"), "高蛋白，補足今日蛋白質缺口"))));

        mockMvc.perform(get("/api/v1/foods/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proteinGapG").value(60))
                .andExpect(jsonPath("$.headline").value("下一餐優先補蛋白質（缺口約 60g）"))
                .andExpect(jsonPath("$.items[0].id").value("chicken-breast"));
    }
}
