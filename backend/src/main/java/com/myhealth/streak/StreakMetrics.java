package com.myhealth.streak;

/**
 * Cumulative numbers used to evaluate {@link AchievementCatalog} badges.
 *
 * @param longestStreak longest overall consecutive-day streak ever achieved
 * @param mealCount     total meals ever logged
 * @param workoutCount  total workouts ever completed (done = true)
 * @param weightCount   total body-weight measurements ever recorded
 */
public record StreakMetrics(int longestStreak, long mealCount, long workoutCount, long weightCount) {
}
