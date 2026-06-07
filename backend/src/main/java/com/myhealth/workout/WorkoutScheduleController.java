package com.myhealth.workout;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.auth.CurrentUser;
import com.myhealth.common.PageEnvelope;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import com.myhealth.workout.WorkoutScheduleDtos.ApplyDayRequest;
import com.myhealth.workout.WorkoutScheduleDtos.GeneratePlanRequest;
import com.myhealth.workout.WorkoutScheduleDtos.WorkoutScheduleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workout-schedules")
public class WorkoutScheduleController {
    private final CurrentUser currentUser;
    private final WorkoutScheduleService scheduleService;
    private final AiEndpointRateLimiter rateLimiter;

    public WorkoutScheduleController(CurrentUser currentUser, WorkoutScheduleService scheduleService,
                                     AiEndpointRateLimiter rateLimiter) {
        this.currentUser = currentUser;
        this.scheduleService = scheduleService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/generate")
    ResponseEntity<WorkoutScheduleResponse> generate(@Valid @RequestBody GeneratePlanRequest request) {
        AppUser user = currentUser.require();
        rateLimiter.checkSchedulePlan(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.generate(user, request));
    }

    @GetMapping
    PageEnvelope<WorkoutScheduleResponse> list() {
        return PageEnvelope.unpaged(scheduleService.list(currentUser.require()));
    }

    @GetMapping("/{id}")
    WorkoutScheduleResponse get(@PathVariable Long id) {
        return scheduleService.get(currentUser.require(), id);
    }

    @PostMapping("/{id}/apply")
    ResponseEntity<WorkoutPlanResponse> apply(@PathVariable Long id, @Valid @RequestBody ApplyDayRequest request) {
        AppUser user = currentUser.require();
        rateLimiter.checkWorkoutGenerate(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.applyDay(user, id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
