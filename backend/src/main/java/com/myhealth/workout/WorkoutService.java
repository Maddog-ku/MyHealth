package com.myhealth.workout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutService {
    private final WorkoutPlanRepository workouts;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;

    public WorkoutService(WorkoutPlanRepository workouts, AiProvider aiProvider, ObjectMapper objectMapper) {
        this.workouts = workouts;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkoutPlanResponse generate(AppUser user, GenerateWorkoutRequest request) {
        int duration = request.durationMin() == null ? 30 : request.durationMin();
        WorkoutIntensity intensity = request.intensity() == null ? WorkoutIntensity.medium : request.intensity();
        String category = request.category().name();
        List<ExerciseItem> items = aiProvider.generateWorkout(category, duration, intensity.name());

        WorkoutPlan plan = new WorkoutPlan();
        plan.setUser(user);
        plan.setDate(request.date());
        plan.setCategory(category);
        plan.setItemsJson(writeItems(items));
        plan.setTotalKcal(items.stream().mapToInt(ExerciseItem::kcal).sum());
        return toResponse(workouts.save(plan));
    }

    public List<WorkoutPlanResponse> list(AppUser user, LocalDate date) {
        return workouts.findByUserIdAndDateOrderByCreatedAtDesc(user.getId(), date).stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkoutPlanResponse get(AppUser user, Long id) {
        return toResponse(findOwned(user, id));
    }

    @Transactional
    public WorkoutPlanResponse complete(AppUser user, Long id) {
        WorkoutPlan plan = findOwned(user, id);
        plan.setDone(true);
        return toResponse(workouts.save(plan));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        workouts.delete(findOwned(user, id));
    }

    private WorkoutPlan findOwned(AppUser user, Long id) {
        return workouts.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Workout plan not found"));
    }

    private WorkoutPlanResponse toResponse(WorkoutPlan plan) {
        return new WorkoutPlanResponse(
                plan.getId(),
                plan.getDate(),
                plan.getCategory(),
                readItems(plan.getItemsJson()),
                plan.getTotalKcal(),
                plan.isDone(),
                plan.getCreatedAt());
    }

    private String writeItems(List<ExerciseItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize workout items", ex);
        }
    }

    private List<ExerciseItem> readItems(String itemsJson) {
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize workout items", ex);
        }
    }
}
