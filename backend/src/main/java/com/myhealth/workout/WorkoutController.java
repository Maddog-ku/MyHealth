package com.myhealth.workout;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.auth.CurrentUser;
import com.myhealth.common.PageEnvelope;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutDtos.CompleteWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.RemoveItemsRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import com.myhealth.workout.WorkoutVolumeDtos.VolumeResponse;
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
    private final WorkoutVolumeService volumeService;
    private final AiEndpointRateLimiter rateLimiter;

    public WorkoutController(CurrentUser currentUser, WorkoutService workoutService,
                            WorkoutVolumeService volumeService, AiEndpointRateLimiter rateLimiter) {
        this.currentUser = currentUser;
        this.workoutService = workoutService;
        this.volumeService = volumeService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/generate")
    ResponseEntity<WorkoutPlanResponse> generate(@Valid @RequestBody GenerateWorkoutRequest request) {
        AppUser user = currentUser.require();
        rateLimiter.checkWorkoutGenerate(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(workoutService.generate(user, request));
    }

    @GetMapping
    PageEnvelope<WorkoutPlanResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return PageEnvelope.unpaged(workoutService.list(currentUser.require(), date));
    }

    @GetMapping("/volume")
    VolumeResponse volume(@RequestParam(required = false) Integer weeks) {
        return volumeService.volume(currentUser.require(), weeks);
    }

    @GetMapping("/{id}")
    WorkoutPlanResponse get(@PathVariable Long id) {
        return workoutService.get(currentUser.require(), id);
    }

    @PostMapping("/{id}/complete")
    WorkoutPlanResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteWorkoutRequest request) {
        Integer actualKcal = request == null ? null : request.actualKcal();
        return workoutService.complete(currentUser.require(), id, actualKcal);
    }

    @PostMapping("/{id}/items/remove")
    WorkoutPlanResponse removeItems(@PathVariable Long id, @Valid @RequestBody RemoveItemsRequest request) {
        return workoutService.removeItems(currentUser.require(), id, request.indices());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        workoutService.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
