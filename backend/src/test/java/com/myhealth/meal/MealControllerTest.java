package com.myhealth.meal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MealController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MealControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MealService mealService;
    @MockBean CurrentUser currentUser;
    @MockBean AiEndpointRateLimiter rateLimiter;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    MealResponse stubMeal(long id) {
        return new MealResponse(
                id, LocalDate.of(2026, 5, 30), "lunch", "雞胸肉沙拉",
                "/api/v1/meals/" + id + "/image",
                List.of(new FoodItem("雞胸肉", 150, 248, 46.5, 5.4, 0.0, 0.92)),
                248, new BigDecimal("46.5"), new BigDecimal("5.4"), new BigDecimal("0.0"),
                "good", Instant.parse("2026-05-30T00:00:00Z"));
    }

    @Test
    void create_returns201_withDescriptionOnly() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.create(any(), isNull(), eq("雞胸肉沙拉"), eq("lunch"), eq(LocalDate.of(2026, 5, 30))))
                .thenReturn(stubMeal(1L));

        mockMvc.perform(multipart("/api/v1/meals")
                        .param("description", "雞胸肉沙拉")
                        .param("slot", "lunch")
                        .param("date", "2026-05-30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("雞胸肉沙拉"))
                .andExpect(jsonPath("$.totalKcal").value(248));

        verify(rateLimiter).checkMealCreate(any());
    }

    @Test
    void create_returns429_whenAiRateLimited() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                "Too many requests. Please retry later."))
                .when(rateLimiter).checkMealCreate(any());

        mockMvc.perform(multipart("/api/v1/meals")
                        .param("description", "雞胸肉沙拉")
                        .param("slot", "lunch")
                        .param("date", "2026-05-30"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    void create_returns201_withImageMultipart() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.create(any(), any(), isNull(), eq("dinner"), isNull())).thenReturn(stubMeal(2L));

        MockMultipartFile image = new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/meals").file(image).param("slot", "dinner"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void create_returns400_whenSlotMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/meals").param("description", "test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("slot"));
    }

    @Test
    void create_propagates400_whenServiceRejectsEmptyInputs() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.create(any(), any(), any(), eq("lunch"), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "image or description is required"));

        mockMvc.perform(multipart("/api/v1/meals").param("slot", "lunch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("image or description is required"));
    }

    @Test
    void list_returnsPageEnvelope() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.list(any(), eq(LocalDate.of(2026, 5, 30))))
                .thenReturn(List.of(stubMeal(1L), stubMeal(2L)));

        mockMvc.perform(get("/api/v1/meals").param("date", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void get_returns404_whenNotOwned() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.get(any(), eq(99L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Meal not found"));

        mockMvc.perform(get("/api/v1/meals/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void update_returns400_whenFoodItemInvalid() throws Exception {
        String body = """
                {"items":[{"name":"","grams":100,"kcal":100,"protein":1,"fat":1,"carb":1,"confidence":0.5}],
                 "aiSuggestion":"保守估算"}
                """;

        mockMvc.perform(put("/api/v1/meals/1").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void update_returns400_whenAiSuggestionContainsUnsupportedCharacters() throws Exception {
        String body = """
                {"items":[{"name":"白飯","grams":100,"kcal":130,"protein":2.5,"fat":0.3,"carb":28,"confidence":0.8}],
                 "aiSuggestion":"<script>alert(1)</script>"}
                """;

        mockMvc.perform(put("/api/v1/meals/1").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("aiSuggestion")));
    }

    @Test
    void delete_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/meals/5"))
                .andExpect(status().isNoContent());

        verify(mealService).delete(eq(user), eq(5L));
    }
}
