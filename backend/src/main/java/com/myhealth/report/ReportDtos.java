package com.myhealth.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ReportDtos {
    private ReportDtos() {
    }

    /**
     * Live-computed numbers for the covered week. Always reflects the latest data
     * (the AI narrative is cached separately and may be older).
     *
     * @param weightStart first known weight in the week (carried forward), or null
     * @param weightEnd   last known weight in the week, or null
     * @param weightDelta weightEnd - weightStart when both known, else null
     */
    public record WeeklySummary(
            int totalIntakeKcal,
            int avgIntakeKcal,
            int totalBurnKcal,
            int netKcal,
            int goalKcal,
            BigDecimal weightStart,
            BigDecimal weightEnd,
            BigDecimal weightDelta,
            int workoutsDone,
            int mealsLogged,
            int daysCovered
    ) {
    }

    /**
     * @param narrative   the cached AI narrative, or null if none has been generated yet
     * @param generatedAt when the narrative was generated, or null if none
     */
    public record WeeklyReportResponse(
            LocalDate weekStart,
            LocalDate weekEnd,
            WeeklySummary summary,
            String narrative,
            Instant generatedAt
    ) {
    }

    /** weekStart may be any date within the target week (normalized to Monday), or null for the current week. */
    public record GenerateReportRequest(LocalDate weekStart) {
    }
}
