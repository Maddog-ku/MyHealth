package com.myhealth.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class UserDtos {
    private UserDtos() {
    }

    public record ProfileUpdateRequest(
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
}
