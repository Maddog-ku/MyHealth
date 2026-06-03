package com.myhealth.workout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ExerciseItem;
import com.myhealth.common.ApiException;
import com.myhealth.common.JsonColumns;
import com.myhealth.common.ErrorCode;
import com.myhealth.user.AppUser;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkoutService {
    private final WorkoutPlanRepository workouts;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public WorkoutService(WorkoutPlanRepository workouts, AiProvider aiProvider, ObjectMapper objectMapper,
                          TransactionTemplate transactionTemplate) {
        this.workouts = workouts;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public WorkoutPlanResponse generate(AppUser user, GenerateWorkoutRequest request) {
        int duration = request.durationMin() == null ? 30 : request.durationMin();
        WorkoutIntensity intensity = request.intensity() == null ? WorkoutIntensity.medium : request.intensity();
        String category = request.category().name();
        // Run the AI generation OUTSIDE any transaction — it can take tens of seconds, and we
        // must not hold a DB connection for that long. Only the persist below is transactional.
        List<ExerciseItem> items = aiProvider.generateWorkout(category, duration, intensity.name());

        return transactionTemplate.execute(status -> {
            WorkoutPlan plan = new WorkoutPlan();
            plan.setUser(user);
            plan.setDate(request.date());
            plan.setCategory(category);
            plan.setItemsJson(writeItems(items));
            plan.setTotalKcal(items.stream().mapToInt(ExerciseItem::kcal).sum());
            return toResponse(workouts.save(plan));
        });
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
    public WorkoutPlanResponse complete(AppUser user, Long id, Integer actualKcal) {
        WorkoutPlan plan = findOwned(user, id);
        plan.setDone(true);
        // Only the exercises whose timer was finished count. Clamp the client-reported
        // amount to [0, totalKcal] so a tampered request can never inflate burn beyond
        // the planned total; a null body means "completed the whole plan".
        int burned = actualKcal == null ? plan.getTotalKcal() : Math.max(0, Math.min(actualKcal, plan.getTotalKcal()));
        plan.setBurnedKcal(burned);
        return toResponse(workouts.save(plan));
    }

    /**
     * Cancel selected exercises from a plan: drop the given item indices, recompute
     * the planned total, and persist. A completed plan is immutable, and at least one
     * index must actually match — otherwise the whole plan should be deleted instead.
     */
    @Transactional
    public WorkoutPlanResponse removeItems(AppUser user, Long id, List<Integer> indices) {
        WorkoutPlan plan = findOwned(user, id);
        if (plan.isDone()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Completed workout cannot be edited");
        }
        List<ExerciseItem> items = readItems(plan.getItemsJson());
        Set<Integer> remove = new HashSet<>(indices);
        List<ExerciseItem> kept = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!remove.contains(i)) {
                kept.add(items.get(i));
            }
        }
        if (kept.size() == items.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "No matching exercises to cancel");
        }
        plan.setItemsJson(writeItems(kept));
        plan.setTotalKcal(kept.stream().mapToInt(ExerciseItem::kcal).sum());
        if (kept.isEmpty()) {
            // Cancelling every exercise leaves nothing to train — drop the whole plan
            // rather than keeping an empty card around.
            workouts.delete(plan);
            return toResponse(plan);
        }
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
                plan.getBurnedKcal(),
                plan.isDone(),
                plan.getCreatedAt());
    }

    private String writeItems(List<ExerciseItem> items) {
        return JsonColumns.write(objectMapper, items);
    }

    private List<ExerciseItem> readItems(String itemsJson) {
        return JsonColumns.read(objectMapper, itemsJson, new TypeReference<List<ExerciseItem>>() {
        });
    }
}
