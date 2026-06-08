package com.myhealth.habit;

import com.myhealth.habit.HabitDtos.DailyHabitsResponse;
import com.myhealth.habit.HabitDtos.HabitItemResponse;
import com.myhealth.habit.HabitDtos.ToggleHabitRequest;
import com.myhealth.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitService {
    /** Lookback window for computing a habit's current streak; comfortably long. */
    private static final int STREAK_WINDOW_DAYS = 120;

    private final HabitLogRepository logs;

    public HabitService(HabitLogRepository logs) {
        this.logs = logs;
    }

    public DailyHabitsResponse daily(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDate from = targetDate.minusDays(STREAK_WINDOW_DAYS - 1);
        // One window query feeds both today's completion and each habit's streak.
        Map<HabitType, HabitLog> completed = new EnumMap<>(HabitType.class);
        Map<HabitType, Set<LocalDate>> daysByType = new EnumMap<>(HabitType.class);
        for (HabitLog log : logs.findByUserIdAndDateBetween(user.getId(), from, targetDate)) {
            daysByType.computeIfAbsent(log.getType(), type -> new HashSet<>()).add(log.getDate());
            if (log.getDate().equals(targetDate)) {
                completed.put(log.getType(), log);
            }
        }
        return toResponse(targetDate, completed, daysByType);
    }

    @Transactional
    public DailyHabitsResponse toggle(AppUser user, HabitType type, ToggleHabitRequest request) {
        LocalDate targetDate = request.date();
        if (Boolean.TRUE.equals(request.completed())) {
            logs.findByUserIdAndDateAndType(user.getId(), targetDate, type).orElseGet(() -> {
                HabitLog log = new HabitLog();
                log.setUser(user);
                log.setDate(targetDate);
                log.setType(type);
                log.setCompletedAt(Instant.now());
                return logs.save(log);
            });
        } else {
            logs.findByUserIdAndDateAndType(user.getId(), targetDate, type).ifPresent(logs::delete);
        }
        return daily(user, targetDate);
    }

    private DailyHabitsResponse toResponse(LocalDate date, Map<HabitType, HabitLog> completed,
                                           Map<HabitType, Set<LocalDate>> daysByType) {
        List<HabitItemResponse> items = Arrays.stream(HabitType.values())
                .map(type -> {
                    HabitLog log = completed.get(type);
                    int streak = currentStreak(daysByType.getOrDefault(type, Set.of()), date);
                    return new HabitItemResponse(type, title(type), description(type), log != null,
                            log == null ? null : log.getCompletedAt(), streak);
                })
                .toList();
        return new DailyHabitsResponse(date, completed.size(), HabitType.values().length, items);
    }

    /**
     * Consecutive days a habit was completed, ending on {@code date}. {@code date} gets a grace
     * day: if it isn't done yet the streak counts back from the day before, so an as-yet-unticked
     * habit doesn't read as already broken.
     */
    static int currentStreak(Set<LocalDate> days, LocalDate date) {
        if (days.isEmpty()) {
            return 0;
        }
        LocalDate cursor = days.contains(date) ? date : date.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private String title(HabitType type) {
        return switch (type) {
            case WATER -> "喝水";
            case STRETCH -> "伸展";
            case PROTEIN -> "蛋白質";
            case SLEEP -> "睡眠";
        };
    }

    private String description(HabitType type) {
        return switch (type) {
            case WATER -> "今天至少補足 6 杯水";
            case STRETCH -> "完成 5 分鐘伸展或活動度練習";
            case PROTEIN -> "每餐都有蛋白質來源";
            case SLEEP -> "睡眠或休息安排達標";
        };
    }
}
