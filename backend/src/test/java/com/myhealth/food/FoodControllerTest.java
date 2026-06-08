package com.myhealth.food;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.food.FoodDtos.FoodResponse;
import java.math.BigDecimal;
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
}
