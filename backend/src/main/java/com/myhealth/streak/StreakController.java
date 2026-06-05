package com.myhealth.streak;

import com.myhealth.auth.CurrentUser;
import com.myhealth.streak.StreakDtos.StreakSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/streak")
public class StreakController {
    private final CurrentUser currentUser;
    private final StreakService streakService;

    public StreakController(CurrentUser currentUser, StreakService streakService) {
        this.currentUser = currentUser;
        this.streakService = streakService;
    }

    /**
     * Live streaks + the badge wall. Reconciles achievements as a side effect (idempotent),
     * so {@code newlyUnlocked} reports anything earned since the last view.
     */
    @GetMapping
    StreakSummaryResponse streak() {
        return streakService.getSummary(currentUser.require());
    }
}
