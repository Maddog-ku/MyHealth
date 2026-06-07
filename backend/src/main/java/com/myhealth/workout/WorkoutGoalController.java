package com.myhealth.workout;

import com.myhealth.auth.CurrentUser;
import com.myhealth.workout.WorkoutGoalDtos.SetWorkoutGoalRequest;
import com.myhealth.workout.WorkoutGoalDtos.WorkoutGoalResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workout-goal")
public class WorkoutGoalController {
    private final CurrentUser currentUser;
    private final WorkoutGoalService goalService;

    public WorkoutGoalController(CurrentUser currentUser, WorkoutGoalService goalService) {
        this.currentUser = currentUser;
        this.goalService = goalService;
    }

    /** Current weekly target + this week's live progress; {@code progress} is null when none is set. */
    @GetMapping
    WorkoutGoalResponse get() {
        return goalService.get(currentUser.require());
    }

    @PutMapping
    WorkoutGoalResponse set(@Valid @RequestBody SetWorkoutGoalRequest request) {
        return goalService.set(currentUser.require(), request);
    }

    @DeleteMapping
    ResponseEntity<Void> delete() {
        goalService.delete(currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
