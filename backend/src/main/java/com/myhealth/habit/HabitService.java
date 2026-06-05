package com.myhealth.habit;

import com.myhealth.habit.HabitDtos.DailyHabitsResponse;
import com.myhealth.habit.HabitDtos.HabitItemResponse;
import com.myhealth.habit.HabitDtos.ToggleHabitRequest;
import com.myhealth.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitService {
    private final HabitLogRepository logs;

    public HabitService(HabitLogRepository logs) {
        this.logs = logs;
    }

    public DailyHabitsResponse daily(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        Map<HabitType, HabitLog> completed = new EnumMap<>(HabitType.class);
        logs.findByUserIdAndDate(user.getId(), targetDate).forEach(log -> completed.put(log.getType(), log));
        return toResponse(targetDate, completed);
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

    private DailyHabitsResponse toResponse(LocalDate date, Map<HabitType, HabitLog> completed) {
        List<HabitItemResponse> items = Arrays.stream(HabitType.values())
                .map(type -> {
                    HabitLog log = completed.get(type);
                    return new HabitItemResponse(type, title(type), description(type), log != null,
                            log == null ? null : log.getCompletedAt());
                })
                .toList();
        return new DailyHabitsResponse(date, completed.size(), HabitType.values().length, items);
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
