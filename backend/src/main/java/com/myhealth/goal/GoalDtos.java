package com.myhealth.goal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class GoalDtos {
    private GoalDtos() {
    }

    /** @param targetDate optional deadline; null means "no deadline". */
    public record SetWeightGoalRequest(
            @NotNull @DecimalMin("20.0") @DecimalMax("400.0") BigDecimal targetWeightKg,
            LocalDate targetDate
    ) {
    }

    /**
     * Live progress toward the goal. Signed values keep direction (negative = losing).
     *
     * @param remainingKg    target − current (0 when reached/overshot in the right way)
     * @param changeSoFarKg  current − start
     * @param progressPct    0–100, clamped; only counts movement toward the target
     * @param ratePerWeekKg  average kg/week since the goal started, or null if &lt;7 days / no change
     * @param projectedDate  estimated attainment date at the current rate, or null if not projectable
     * @param onTrack        whether the projection meets {@code targetDate}; null if it can't be judged
     */
    public record WeightGoalProgress(
            BigDecimal targetWeightKg,
            BigDecimal startWeightKg,
            BigDecimal currentWeightKg,
            LocalDate startDate,
            LocalDate targetDate,
            BigDecimal remainingKg,
            BigDecimal changeSoFarKg,
            int progressPct,
            Double ratePerWeekKg,
            LocalDate projectedDate,
            Boolean onTrack,
            boolean achieved,
            Instant createdAt
    ) {
    }

    /** GET/PUT envelope; {@code progress} is null when the user has no goal set. */
    public record WeightGoalResponse(WeightGoalProgress progress) {
    }
}
