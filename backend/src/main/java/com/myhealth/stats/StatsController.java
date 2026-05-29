package com.myhealth.stats;

import com.myhealth.auth.CurrentUser;
import com.myhealth.stats.StatsDtos.DailyStatsResponse;
import com.myhealth.stats.StatsDtos.RangeStatsResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {
    private final CurrentUser currentUser;
    private final StatsService statsService;

    public StatsController(CurrentUser currentUser, StatsService statsService) {
        this.currentUser = currentUser;
        this.statsService = statsService;
    }

    @GetMapping("/daily")
    DailyStatsResponse daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return statsService.daily(currentUser.require(), date);
    }

    @GetMapping("/range")
    RangeStatsResponse range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.range(currentUser.require(), from, to);
    }
}
