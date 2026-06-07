package com.myhealth.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhealth.auth.AuthDtos.ProfileResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.auth.CurrentUser;
import com.myhealth.auth.JwtAuthenticationFilter;
import com.myhealth.common.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService userService;
    @MockBean SessionService sessionService;
    @MockBean com.myhealth.auth.AuthService authService;
    @MockBean CurrentUser currentUser;

    AppUser stubUser() {
        AppUser u = new AppUser();
        u.setEmail("demo@example.com");
        u.setName("Demo");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void me_returns200_andRouteDelegatesToService() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(userService.me(any())).thenReturn(new UserResponse(
                1L, "demo@example.com", "Demo", Role.USER, null, Instant.parse("2026-05-30T00:00:00Z")));

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@example.com"));
    }

    @Test
    void updateProfile_returns200_whenValid() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(userService.updateProfile(any(), any())).thenReturn(new ProfileResponse(
                Gender.male, new BigDecimal("175"), new BigDecimal("70"),
                null, null, null, null, null, null,
                Goal.maintain, List.of(), Experience.beginner, "male", "dark", "zh-TW"));

        String body = "{\"gender\":\"male\",\"heightCm\":175,\"weightKg\":70,\"theme\":\"dark\"}";

        mockMvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.heightCm").value(175));
    }

    @Test
    void updateProfile_returns400_whenMissingRequiredFields() throws Exception {
        String body = "{\"theme\":\"dark\"}";

        mockMvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field").value(
                        org.hamcrest.Matchers.hasItems("gender", "heightCm", "weightKg")));
    }

    @Test
    void updateProfile_returns400_whenHeightOutOfRange() throws Exception {
        String body = "{\"gender\":\"male\",\"heightCm\":10,\"weightKg\":70}";

        mockMvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("heightCm")));
    }

    @Test
    void updateProfile_returns400_whenEquipmentContainsUnsupportedCharacters() throws Exception {
        String body = "{\"gender\":\"male\",\"heightCm\":175,\"weightKg\":70,\"equipment\":[\"啞鈴<script>\"]}";

        mockMvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("equipment[0]")));
    }

    @Test
    void updateProfile_returns400_whenWeightHasTooManyDecimals() throws Exception {
        String body = "{\"gender\":\"male\",\"heightCm\":175,\"weightKg\":70.123}";

        mockMvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("weightKg")));
    }

    @Test
    void changePassword_returns204_andForwardsRefreshTokenHeader() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        String body = "{\"currentPassword\":\"OldPass1\",\"newPassword\":\"NewPass2\"}";

        mockMvc.perform(post("/api/v1/me/password")
                        .header("X-Refresh-Token", "raw-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(eq(user), eq("OldPass1"), eq("NewPass2"), eq("raw-token"));
    }

    @Test
    void changePassword_returns400_whenNewPasswordViolatesPolicy() throws Exception {
        String body = "{\"currentPassword\":\"OldPass1\",\"newPassword\":\"short\"}";

        mockMvc.perform(post("/api/v1/me/password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem("newPassword")));
    }

    @Test
    void sessions_returnsList_andForwardsCurrentRefreshTokenHeader() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(sessionService.list(any(), eq("raw-token"))).thenReturn(
                new SessionDtos.SessionListResponse(List.of(new SessionDtos.SessionResponse(
                        5L, "Chrome · macOS", Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-06-07T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"), true))));

        mockMvc.perform(get("/api/v1/me/sessions").header("X-Refresh-Token", "raw-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].id").value(5))
                .andExpect(jsonPath("$.sessions[0].device").value("Chrome · macOS"))
                .andExpect(jsonPath("$.sessions[0].current").value(true));
    }

    @Test
    void sessions_worksWithoutCurrentTokenHeader() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(sessionService.list(any(), eq((String) null)))
                .thenReturn(new SessionDtos.SessionListResponse(List.of()));

        mockMvc.perform(get("/api/v1/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray());
    }

    @Test
    void revokeSession_returns204_andDelegates() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/me/sessions/9"))
                .andExpect(status().isNoContent());

        verify(sessionService).revoke(eq(user), eq(9L));
    }

    @Test
    void revokeOthers_returns200_withRefreshedList() throws Exception {
        when(currentUser.require()).thenReturn(stubUser());
        when(sessionService.revokeOthers(any(), eq("raw-token")))
                .thenReturn(new SessionDtos.SessionListResponse(List.of()));

        mockMvc.perform(post("/api/v1/me/sessions/revoke-others")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"raw-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray());
    }

    @Test
    void revokeOthers_returns400_whenRefreshTokenBlank() throws Exception {
        mockMvc.perform(post("/api/v1/me/sessions/revoke-others")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteAccount_returns204() throws Exception {
        AppUser user = stubUser();
        when(currentUser.require()).thenReturn(user);

        mockMvc.perform(delete("/api/v1/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(eq(user));
    }
}
