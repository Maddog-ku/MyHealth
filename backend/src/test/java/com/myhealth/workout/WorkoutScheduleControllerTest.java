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
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import com.myhealth.workout.WorkoutScheduleDtos.ScheduleDayDto;
import com.myhealth.workout.WorkoutScheduleDtos.WorkoutScheduleResponse;
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

@WebMvcTest(controllers = WorkoutScheduleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WorkoutScheduleControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean WorkoutScheduleService scheduleService;
    @MockBean CurrentUser currentUser;
    @MockBean AiEndpointRateLimiter rateLimiter;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("u@example.com");
        u.setRole(Role.USER);
        return u;
    }

    WorkoutScheduleResponse stubSchedule(long id) {
        return new WorkoutScheduleResponse(id, "增肌", LocalDate.of(2026, 6, 1), 2, 3, "medium",
                List.of(
                        new ScheduleDayDto(1, false, "legs", 40, "下肢肌力"),
                        new ScheduleDayDto(2, true, null, 0, "休息與恢復")),
                Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void generate_returns201_andCallsService_andRateLimiter() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(scheduleService.generate(any(), any())).thenReturn(stubSchedule(1L));

        String body = "{\"startDate\":\"2026-06-01\",\"daysPerWeek\":3,\"weeks\":2,\"intensity\":\"medium\"}";

        mockMvc.perform(post("/api/v1/workout-schedules/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.goal").value("增肌"))
                .andExpect(jsonPath("$.days[0].category").value("legs"));

        verify(rateLimiter).checkSchedulePlan(any());
    }

    @Test
    void generate_returns429_whenAiRateLimited() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, "Too many requests."))
                .when(rateLimiter).checkSchedulePlan(any());

        String body = "{\"startDate\":\"2026-06-01\",\"daysPerWeek\":3,\"weeks\":2,\"intensity\":\"medium\"}";

        mockMvc.perform(post("/api/v1/workout-schedules/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    void generate_returns400_whenDaysPerWeekOutOfRange() throws Exception {
        String body = "{\"startDate\":\"2026-06-01\",\"daysPerWeek\":9,\"weeks\":2}";

        mockMvc.perform(post("/api/v1/workout-schedules/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("daysPerWeek")));
    }

    @Test
    void generate_returns400_whenWeeksMissing() throws Exception {
        String body = "{\"startDate\":\"2026-06-01\",\"daysPerWeek\":3}";

        mockMvc.perform(post("/api/v1/workout-schedules/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("weeks")));
    }

    @Test
    void list_wrapsInPageEnvelope() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(scheduleService.list(any())).thenReturn(List.of(stubSchedule(1L), stubSchedule(2L)));

        mockMvc.perform(get("/api/v1/workout-schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void apply_returns201_andCallsService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(scheduleService.applyDay(any(), eq(5L), any())).thenReturn(
                new com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse(
                        77L, LocalDate.of(2026, 6, 1), "legs", List.of(), 200, null, false,
                        Instant.parse("2026-06-01T00:00:00Z")));

        String body = "{\"date\":\"2026-06-01\",\"weekday\":1}";

        mockMvc.perform(post("/api/v1/workout-schedules/5/apply")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.category").value("legs"));

        verify(rateLimiter).checkWorkoutGenerate(any());
    }

    @Test
    void apply_returns400_whenWeekdayOutOfRange() throws Exception {
        String body = "{\"date\":\"2026-06-01\",\"weekday\":8}";

        mockMvc.perform(post("/api/v1/workout-schedules/5/apply")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("weekday")));
    }

    @Test
    void get_returns404_whenServiceThrowsNotFound() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(scheduleService.get(any(), eq(99L))).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Workout schedule not found"));

        mockMvc.perform(get("/api/v1/workout-schedules/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void delete_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/workout-schedules/3"))
                .andExpect(status().isNoContent());

        verify(scheduleService).delete(eq(user), eq(3L));
    }
}
