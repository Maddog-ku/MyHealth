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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.meal.MealDtos.FavoriteMealResponse;
import com.myhealth.meal.MealDtos.MealPreviewResponse;
import com.myhealth.meal.MealDtos.MealResponse;
import com.myhealth.meal.MealDtos.RecentMealResponse;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
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

    FavoriteMealResponse stubFavorite(long id) {
        return new FavoriteMealResponse(
                id, "健身午餐", "lunch", "雞胸肉沙拉",
                List.of(new FoodItem("雞胸肉", 150, 248, 46.5, 5.4, 0.0, 1.0)),
                248, new BigDecimal("46.5"), new BigDecimal("5.4"), new BigDecimal("0.0"),
                null, Instant.parse("2026-05-30T00:00:00Z"));
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
    void preview_returns200_withoutPersistingMeal() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.preview(any(), isNull(), eq("雞胸肉沙拉"), eq("lunch"), eq(LocalDate.of(2026, 5, 30))))
                .thenReturn(new MealPreviewResponse(
                        LocalDate.of(2026, 5, 30), "lunch", "雞胸肉沙拉",
                        List.of(new FoodItem("雞胸肉", 150, 248, 46.5, 5.4, 0, 0.92)),
                        248, new BigDecimal("46.5"), new BigDecimal("5.4"), BigDecimal.ZERO,
                        "good"));

        mockMvc.perform(multipart("/api/v1/meals/preview")
                        .param("description", "雞胸肉沙拉")
                        .param("slot", "lunch")
                        .param("date", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("雞胸肉沙拉"))
                .andExpect(jsonPath("$.items[0].name").value("雞胸肉"))
                .andExpect(jsonPath("$.totalKcal").value(248));

        verify(rateLimiter).checkMealCreate(any());
    }

    @Test
    void confirm_returns201_withConfirmedItems() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.confirm(any(), isNull(), eq("雞胸肉沙拉"), eq("lunch"), eq(LocalDate.of(2026, 5, 30)),
                eq("[{\"name\":\"雞胸肉\",\"grams\":150,\"kcal\":248,\"protein\":46.5,\"fat\":5.4,\"carb\":0,\"confidence\":1}]"),
                eq("good"))).thenReturn(stubMeal(3L));

        mockMvc.perform(multipart("/api/v1/meals/confirm")
                        .param("description", "雞胸肉沙拉")
                        .param("slot", "lunch")
                        .param("date", "2026-05-30")
                        .param("items", "[{\"name\":\"雞胸肉\",\"grams\":150,\"kcal\":248,\"protein\":46.5,\"fat\":5.4,\"carb\":0,\"confidence\":1}]")
                        .param("aiSuggestion", "good"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
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
    void recent_returnsPageEnvelope() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.recent(any(), eq(LocalDate.of(2026, 6, 1)), eq(3)))
                .thenReturn(List.of(new RecentMealResponse(
                        1L, LocalDate.of(2026, 5, 30), "雞胸肉", "lunch", "雞胸肉沙拉",
                        List.of(), 248, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        Instant.parse("2026-05-30T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/meals/recent").param("beforeDate", "2026-06-01").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("雞胸肉"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void favorites_returnsList() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.favorites(any())).thenReturn(List.of(stubFavorite(9L)));

        mockMvc.perform(get("/api/v1/meals/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].name").value("健身午餐"));
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
    void image_returnsPrivateNoStoreCacheHeaders() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.loadImage(any(), eq(1L)))
                .thenReturn(new FileStorageService.StoredFile(new ByteArrayResource(new byte[]{1, 2, 3}), "image/png"));

        mockMvc.perform(get("/api/v1/meals/1/image"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @Test
    void update_returns200_andDelegatesToService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.update(any(), eq(1L), any())).thenReturn(stubMeal(1L));

        String body = """
                {"items":[{"name":"雞胸肉","grams":180,"kcal":297,"protein":55.8,"fat":6.5,"carb":0,"confidence":1.0}],
                 "aiSuggestion":null}
                """;

        mockMvc.perform(put("/api/v1/meals/1").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalKcal").value(248));

        verify(mealService).update(any(), eq(1L), any());
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
    void favorite_returns201_andDelegatesToService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.favorite(any(), eq(5L), any())).thenReturn(stubFavorite(10L));

        mockMvc.perform(post("/api/v1/meals/5/favorite")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"健身午餐\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("健身午餐"));

        verify(mealService).favorite(any(), eq(5L), any());
    }

    @Test
    void favorite_returns400_whenNameContainsMarkup() throws Exception {
        mockMvc.perform(post("/api/v1/meals/5/favorite")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("name")));
    }

    @Test
    void copyMeal_returns201() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.copyMeal(any(), eq(5L), any())).thenReturn(stubMeal(11L));

        mockMvc.perform(post("/api/v1/meals/5/copy")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-01\",\"slot\":\"dinner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void copyMeal_returns400_whenDateMissing() throws Exception {
        mockMvc.perform(post("/api/v1/meals/5/copy")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"slot\":\"dinner\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("date")));
    }

    @Test
    void copyFavorite_returns201() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(mealService.copyFavorite(any(), eq(9L), any())).thenReturn(stubMeal(12L));

        mockMvc.perform(post("/api/v1/meals/favorites/9/copy")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void deleteFavorite_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/meals/favorites/9"))
                .andExpect(status().isNoContent());

        verify(mealService).deleteFavorite(eq(user), eq(9L));
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
