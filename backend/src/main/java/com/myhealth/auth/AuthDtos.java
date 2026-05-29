package com.myhealth.auth;

import com.myhealth.user.Experience;
import com.myhealth.user.Gender;
import com.myhealth.user.Goal;
import com.myhealth.user.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Size(max = 100) String name,
            @NotNull Gender gender,
            @NotNull @DecimalMin("50") @DecimalMax("250") BigDecimal heightCm,
            @NotNull @DecimalMin("20") @DecimalMax("300") BigDecimal weightKg,
            Integer age,
            BigDecimal bodyFatPct,
            BigDecimal muscleMassKg,
            Integer bmrKcal,
            BigDecimal waistCm,
            BigDecimal bodyWaterPct,
            Goal goal,
            List<String> equipment,
            Experience experience,
            String theme,
            String language
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserSummary user
    ) {
    }

    public record UserSummary(Long id, String email, String name, Role role) {
    }

    public record UserResponse(
            Long id,
            String email,
            String name,
            Role role,
            ProfileResponse profile,
            Instant createdAt
    ) {
    }

    public record ProfileResponse(
            Gender gender,
            BigDecimal heightCm,
            BigDecimal weightKg,
            Integer age,
            BigDecimal bodyFatPct,
            BigDecimal muscleMassKg,
            Integer bmrKcal,
            BigDecimal waistCm,
            BigDecimal bodyWaterPct,
            Goal goal,
            List<String> equipment,
            Experience experience,
            String theme,
            String language
    ) {
    }
}
