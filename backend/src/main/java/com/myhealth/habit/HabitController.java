package com.myhealth.habit;

import com.myhealth.auth.CurrentUser;
import com.myhealth.habit.HabitDtos.DailyHabitsResponse;
import com.myhealth.habit.HabitDtos.ToggleHabitRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/habits")
public class HabitController {
    private final CurrentUser currentUser;
    private final HabitService habitService;

    public HabitController(CurrentUser currentUser, HabitService habitService) {
        this.currentUser = currentUser;
        this.habitService = habitService;
    }

    @GetMapping("/daily")
    DailyHabitsResponse daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return habitService.daily(currentUser.require(), date);
    }

    @PostMapping("/{type}/toggle")
    DailyHabitsResponse toggle(@PathVariable HabitType type, @Valid @RequestBody ToggleHabitRequest request) {
        return habitService.toggle(currentUser.require(), type, request);
    }
}
