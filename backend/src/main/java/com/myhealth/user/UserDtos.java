package com.myhealth.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class UserDtos {
    private UserDtos() {
    }

    public record ProfileUpdateRequest(
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
            @Pattern(regexp = "male|female") String assistantAvatar,
            @Pattern(regexp = "light|dark|system") String theme,
            @Pattern(regexp = "zh-TW|en") String language
    ) {
    }
}
