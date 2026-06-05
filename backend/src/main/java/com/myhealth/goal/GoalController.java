package com.myhealth.goal;

import com.myhealth.auth.CurrentUser;
import com.myhealth.goal.GoalDtos.SetWeightGoalRequest;
import com.myhealth.goal.GoalDtos.WeightGoalResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weight-goal")
public class GoalController {
    private final CurrentUser currentUser;
    private final GoalService goalService;

    public GoalController(CurrentUser currentUser, GoalService goalService) {
        this.currentUser = currentUser;
        this.goalService = goalService;
    }

    /** Current goal + live progress; {@code progress} is null when none is set. */
    @GetMapping
    WeightGoalResponse get() {
        return goalService.get(currentUser.require());
    }

    /** Create or replace the goal (re-anchors start weight/date to now). */
    @PutMapping
    WeightGoalResponse set(@Valid @RequestBody SetWeightGoalRequest request) {
        return goalService.set(currentUser.require(), request);
    }

    @DeleteMapping
    ResponseEntity<Void> delete() {
        goalService.delete(currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
