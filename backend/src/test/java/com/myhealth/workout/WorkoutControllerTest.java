package com.myhealth.workout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WorkoutController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WorkoutControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean WorkoutService workoutService;
    @MockBean WorkoutVolumeService volumeService;
    @MockBean CurrentUser currentUser;
    @MockBean AiEndpointRateLimiter rateLimiter;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    WorkoutPlanResponse stubPlan(long id) {
        return new WorkoutPlanResponse(
                id, LocalDate.of(2026, 5, 30), "abs",
                List.of(new ExerciseItem("捲腹", 4, "15", 45, 45, 40, "n", List.of())),
                40, null, false, Instant.parse("2026-05-30T00:00:00Z"));
    }

    @Test
    void generate_returns201_andCallsService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(workoutService.generate(any(), any())).thenReturn(stubPlan(1L));

        String body = "{\"date\":\"2026-05-30\",\"category\":\"abs\",\"durationMin\":30,\"intensity\":\"medium\"}";

        mockMvc.perform(post("/api/v1/workouts/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.category").value("abs"))
                .andExpect(jsonPath("$.items[0].name").value("捲腹"));

        verify(rateLimiter).checkWorkoutGenerate(any());
    }

    @Test
    void generate_returns201_forWaistCategory() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        WorkoutPlanResponse waistPlan = new WorkoutPlanResponse(
                9L, LocalDate.of(2026, 5, 30), "waist",
                List.of(new ExerciseItem("側棒式", 3, "每側30s", 45, 60, 35, "髖部抬高", List.of())),
                35, null, false, Instant.parse("2026-05-30T00:00:00Z"));
        when(workoutService.generate(any(), any())).thenReturn(waistPlan);

        String body = "{\"date\":\"2026-05-30\",\"category\":\"waist\",\"durationMin\":30,\"intensity\":\"medium\"}";

        mockMvc.perform(post("/api/v1/workouts/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("waist"))
                .andExpect(jsonPath("$.items[0].name").value("側棒式"));
    }

    @Test
    void generate_returns429_whenAiRateLimited() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                "Too many requests. Please retry later."))
                .when(rateLimiter).checkWorkoutGenerate(any());

        String body = "{\"date\":\"2026-05-30\",\"category\":\"abs\",\"durationMin\":30,\"intensity\":\"medium\"}";

        mockMvc.perform(post("/api/v1/workouts/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    void generate_returns400_whenCategoryBlank() throws Exception {
        String body = "{\"date\":\"2026-05-30\",\"category\":\"\",\"durationMin\":30}";

        mockMvc.perform(post("/api/v1/workouts/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void generate_returns400_whenDurationOutOfRange() throws Exception {
        String body = "{\"date\":\"2026-05-30\",\"category\":\"abs\",\"durationMin\":5}";

        mockMvc.perform(post("/api/v1/workouts/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("durationMin")));
    }

    @Test
    void generate_returns400_whenEquipmentOverrideContainsUnsupportedCharacters() throws Exception {
        String body = "{\"date\":\"2026-05-30\",\"category\":\"abs\",\"durationMin\":30,\"equipmentOverride\":[\"啞鈴<script>\"]}";

        mockMvc.perform(post("/api/v1/workouts/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void list_wrapsInPageEnvelope() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(workoutService.list(any(), eq(LocalDate.of(2026, 5, 30))))
                .thenReturn(List.of(stubPlan(1L), stubPlan(2L)));

        mockMvc.perform(get("/api/v1/workouts").param("date", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void list_returns400_whenDateMissing() throws Exception {
        mockMvc.perform(get("/api/v1/workouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("date"));
    }

    @Test
    void list_returns400_whenDateMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/workouts").param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("date"));
    }

    @Test
    void volume_returnsAnalytics_andPassesWeeksParam() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(volumeService.volume(any(), eq(8))).thenReturn(
                new com.myhealth.workout.WorkoutVolumeDtos.VolumeResponse(
                        LocalDate.of(2026, 5, 4), LocalDate.of(2026, 6, 7), 8, 12, 140, 1500, 9, 1.5,
                        List.of(new com.myhealth.workout.WorkoutVolumeDtos.CategoryVolume("legs", 5, 60, 700)),
                        List.of(new com.myhealth.workout.WorkoutVolumeDtos.WeekVolume(
                                LocalDate.of(2026, 5, 4), 2, 24, 240))));

        mockMvc.perform(get("/api/v1/workouts/volume").param("weeks", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(12))
                .andExpect(jsonPath("$.byCategory[0].category").value("legs"))
                .andExpect(jsonPath("$.series[0].sessions").value(2));
    }

    @Test
    void volume_worksWithoutWeeksParam() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(volumeService.volume(any(), eq((Integer) null))).thenReturn(
                new com.myhealth.workout.WorkoutVolumeDtos.VolumeResponse(
                        LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 7), 4, 0, 0, 0, 0, 0.0,
                        List.of(), List.of()));

        mockMvc.perform(get("/api/v1/workouts/volume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks").value(4))
                .andExpect(jsonPath("$.totalSessions").value(0));
    }

    @Test
    void get_returns404_whenServiceThrowsNotFound() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(workoutService.get(any(), eq(99L))).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Workout plan not found"));

        mockMvc.perform(get("/api/v1/workouts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void complete_returns200_andCallsService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        WorkoutPlanResponse done = new WorkoutPlanResponse(
                7L, LocalDate.of(2026, 5, 30), "abs",
                List.of(), 80, 50, true, Instant.parse("2026-05-30T00:00:00Z"));
        when(workoutService.complete(any(), eq(7L), any())).thenReturn(done);

        mockMvc.perform(post("/api/v1/workouts/7/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.burnedKcal").value(50));
    }

    @Test
    void complete_returns400_whenNoteContainsUnsupportedCharacters() throws Exception {
        String body = "{\"actualKcal\":120,\"note\":\"<script>alert(1)</script>\"}";

        mockMvc.perform(post("/api/v1/workouts/7/complete")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("note")));
    }

    @Test
    void removeItems_returns200_andCallsService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(workoutService.removeItems(any(), eq(5L), eq(List.of(0, 2)))).thenReturn(stubPlan(5L));

        mockMvc.perform(post("/api/v1/workouts/5/items/remove")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"indices\":[0,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void removeItems_returns400_whenIndicesEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/workouts/5/items/remove")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"indices\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void delete_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/workouts/3"))
                .andExpect(status().isNoContent());

        verify(workoutService).delete(eq(user), eq(3L));
    }
}
