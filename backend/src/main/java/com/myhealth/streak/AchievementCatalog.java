package com.myhealth.streak;

/**
 * The fixed catalog of unlockable badges. Lives in code (not the DB): the
 * {@code achievements} table only records which of these a user has unlocked.
 * Each badge is earned when the user's value for {@link Metric} reaches {@code threshold}.
 */
public enum AchievementCatalog {
    STREAK_3(Metric.LONGEST_STREAK, 3, "三日連勝", "🔥", "連續 3 天記錄健康數據"),
    STREAK_7(Metric.LONGEST_STREAK, 7, "一週不間斷", "🔥", "連續 7 天記錄健康數據"),
    STREAK_30(Metric.LONGEST_STREAK, 30, "月度鐵人", "🏆", "連續 30 天記錄健康數據"),
    MEALS_50(Metric.MEAL_COUNT, 50, "飲食記錄家", "🍱", "累計記錄 50 筆餐點"),
    MEALS_100(Metric.MEAL_COUNT, 100, "飲食達人", "🥗", "累計記錄 100 筆餐點"),
    WORKOUTS_10(Metric.WORKOUT_COUNT, 10, "動起來", "💪", "累計完成 10 次訓練"),
    WORKOUTS_50(Metric.WORKOUT_COUNT, 50, "健身常客", "🏋️", "累計完成 50 次訓練"),
    FIRST_WEIGHT(Metric.WEIGHT_LOGGED, 1, "踏出第一步", "⚖️", "第一次記錄體重");

    /** Which cumulative number this badge is measured against. */
    public enum Metric {
        LONGEST_STREAK, MEAL_COUNT, WORKOUT_COUNT, WEIGHT_LOGGED
    }

    private final Metric metric;
    private final int threshold;
    private final String title;
    private final String emoji;
    private final String description;

    AchievementCatalog(Metric metric, int threshold, String title, String emoji, String description) {
        this.metric = metric;
        this.threshold = threshold;
        this.title = title;
        this.emoji = emoji;
        this.description = description;
    }

    public Metric metric() {
        return metric;
    }

    public int threshold() {
        return threshold;
    }

    public String title() {
        return title;
    }

    public String emoji() {
        return emoji;
    }

    public String description() {
        return description;
    }

    /** The user's current value for this badge's metric, clamped at the threshold. */
    public int progress(StreakMetrics metrics) {
        long value = switch (metric) {
            case LONGEST_STREAK -> metrics.longestStreak();
            case MEAL_COUNT -> metrics.mealCount();
            case WORKOUT_COUNT -> metrics.workoutCount();
            case WEIGHT_LOGGED -> metrics.weightCount();
        };
        return (int) Math.min(value, threshold);
    }

    public boolean achieved(StreakMetrics metrics) {
        return progress(metrics) >= threshold;
    }
}
