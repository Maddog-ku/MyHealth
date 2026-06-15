package com.myhealth.streak;

/**
 * Cumulative numbers used to evaluate {@link AchievementCatalog} badges.
 *
 * @param longestStreak  longest overall consecutive-day streak ever achieved
 * @param mealCount      total meals ever logged
 * @param workoutCount   total workouts ever completed (done = true)
 * @param weightCount    total body-weight measurements ever recorded
 * @param photoMealCount     total meals logged with a photo
 * @param weeklyGoalHits     number of weeks (in the lookback window) that met the weekly workout goal
 * @param scheduleCompletions number of scheduled workout days completed from an active workout schedule
 * @param longestHabitStreak longest consecutive-day completion run for any single daily habit
 */
public record StreakMetrics(int longestStreak, long mealCount, long workoutCount, long weightCount,
                            long photoMealCount, int weeklyGoalHits, int scheduleCompletions,
                            int longestHabitStreak) {
}
