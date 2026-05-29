package com.myhealth.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.auth.AuthDtos.AuthResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.auth.AuthDtos.UserSummary;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.GlobalExceptionHandler;
import com.myhealth.user.Role;
import java.time.Instant;
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

@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;

    @Test
    void register_returns201_andCallsService() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(
                1L, "demo@example.com", "Demo", Role.USER, null, Instant.parse("2026-05-30T00:00:00Z")));

        String body = """
                {
                  "email": "demo@example.com",
                  "password": "secret123",
                  "name": "Demo",
                  "gender": "male",
                  "heightCm": 175,
                  "weightKg": 70
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(authService).register(argThat(req -> "demo@example.com".equals(req.email())));
    }

    @Test
    void register_returns400_whenValidationFails() throws Exception {
        String body = """
                {"email": "not-an-email", "password": "x", "name": "", "gender": "male", "heightCm": 175, "weightKg": 70}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void register_returns409_whenServiceThrowsConflict() throws Exception {
        when(authService.register(any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Email already registered"));

        String body = """
                {"email":"x@y.z","password":"secret123","name":"X","gender":"male","heightCm":175,"weightKg":70}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void login_returns200_withTokensAndUser() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse(
                "access-token-value", "refresh-token-uuid", "Bearer", 900L,
                new UserSummary(1L, "demo@example.com", "Demo", Role.USER)));

        String body = "{\"email\":\"demo@example.com\",\"password\":\"secret123\"}";

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-uuid"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("demo@example.com"));
    }

    @Test
    void login_returns401_whenServiceThrowsUnauthorized() throws Exception {
        when(authService.login(any())).thenThrow(
                new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid email or password"));

        String body = "{\"email\":\"demo@example.com\",\"password\":\"wrong\"}";

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void refresh_returns200_withNewTokens() throws Exception {
        when(authService.refresh(any())).thenReturn(new AuthResponse(
                "new-access", "new-refresh", "Bearer", 900L,
                new UserSummary(1L, "demo@example.com", "Demo", Role.USER)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some-uuid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void refresh_returns401_whenTokenInvalid() throws Exception {
        when(authService.refresh(any())).thenThrow(
                new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token reuse detected"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"reused\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_returns204_andCallsService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"abc\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout("abc");
    }

    @Test
    void logout_returns400_whenRefreshTokenBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
