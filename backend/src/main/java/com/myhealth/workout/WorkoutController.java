package com.myhealth.workout;

import com.myhealth.auth.CurrentUser;
import com.myhealth.common.PageEnvelope;
import com.myhealth.workout.WorkoutDtos.CompleteWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {
    private final CurrentUser currentUser;
    private final WorkoutService workoutService;

    public WorkoutController(CurrentUser currentUser, WorkoutService workoutService) {
        this.currentUser = currentUser;
        this.workoutService = workoutService;
    }

    @PostMapping("/generate")
    ResponseEntity<WorkoutPlanResponse> generate(@Valid @RequestBody GenerateWorkoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workoutService.generate(currentUser.require(), request));
    }

    @GetMapping
    PageEnvelope<WorkoutPlanResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return PageEnvelope.unpaged(workoutService.list(currentUser.require(), date));
    }

    @GetMapping("/{id}")
    WorkoutPlanResponse get(@PathVariable Long id) {
        return workoutService.get(currentUser.require(), id);
    }

    @PostMapping("/{id}/complete")
    WorkoutPlanResponse complete(@PathVariable Long id, @RequestBody(required = false) CompleteWorkoutRequest request) {
        return workoutService.complete(currentUser.require(), id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        workoutService.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
