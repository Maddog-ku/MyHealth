package com.myhealth.auth;

import com.myhealth.user.Experience;
import com.myhealth.user.Gender;
import com.myhealth.user.Goal;
import com.myhealth.user.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128) @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])[A-Za-z0-9]+$") String password,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N}\\s._-]+$") String name,
            @NotNull Gender gender,
            @NotNull @DecimalMin("50") @DecimalMax("250") @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
            @NotNull @DecimalMin("20") @DecimalMax("300") @Digits(integer = 3, fraction = 1) BigDecimal weightKg,
            @Min(1) @Max(120) Integer age,
            @DecimalMin("1") @DecimalMax("70") @Digits(integer = 2, fraction = 1) BigDecimal bodyFatPct,
            @DecimalMin("1") @DecimalMax("150") @Digits(integer = 3, fraction = 1) BigDecimal muscleMassKg,
            @Min(500) @Max(5000) Integer bmrKcal,
            @DecimalMin("30") @DecimalMax("200") @Digits(integer = 3, fraction = 1) BigDecimal waistCm,
            @DecimalMin("1") @DecimalMax("90") @Digits(integer = 2, fraction = 1) BigDecimal bodyWaterPct,
            Goal goal,
            @Size(max = 20) List<@NotBlank @Size(max = 30) @Pattern(regexp = "^[\\p{L}\\p{N}\\s._-]+$") String> equipment,
            Experience experience,
            @Pattern(regexp = "light|dark|system") String theme,
            @Pattern(regexp = "zh-TW|en") String language
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128) @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])[A-Za-z0-9]+$") String password
    ) {
    }

    public record RefreshRequest(@NotBlank @Size(min = 20, max = 200) String refreshToken) {
    }

    public record LogoutRequest(@NotBlank @Size(min = 20, max = 200) String refreshToken) {
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
