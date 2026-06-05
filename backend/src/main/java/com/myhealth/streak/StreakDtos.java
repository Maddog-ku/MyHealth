package com.myhealth.streak;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StreakDtos {
    private StreakDtos() {
    }

    /**
     * One consecutive-day streak.
     *
     * @param current        days in the streak ending today (or yesterday, if today
     *                       has no activity yet — today is given a grace day)
     * @param longest        longest run of consecutive active days in the window
     * @param lastActiveDate most recent active day, or null if there is none
     */
    public record StreakInfo(int current, int longest, LocalDate lastActiveDate) {
    }

    /**
     * A badge plus this user's progress toward it.
     *
     * @param unlockedAt when it was unlocked, or null if still locked
     */
    public record AchievementView(
            String code,
            String title,
            String emoji,
            String description,
            int threshold,
            int progress,
            boolean unlocked,
            Instant unlockedAt
    ) {
    }

    /**
     * @param newlyUnlocked codes unlocked by the reconcile this request triggered
     *                      (lets the UI celebrate); empty when nothing new
     */
    public record StreakSummaryResponse(
            StreakInfo mealStreak,
            StreakInfo workoutStreak,
            StreakInfo overallStreak,
            List<AchievementView> achievements,
            List<String> newlyUnlocked
    ) {
    }
}
