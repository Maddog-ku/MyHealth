package com.myhealth.workout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.ai.AiProvider;
import com.myhealth.ai.AiProvider.ScheduleDay;
import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.common.JsonColumns;
import com.myhealth.user.AppUser;
import com.myhealth.user.Profile;
import com.myhealth.workout.WorkoutDtos.GenerateWorkoutRequest;
import com.myhealth.workout.WorkoutDtos.WorkoutPlanResponse;
import com.myhealth.workout.WorkoutScheduleDtos.ApplyDayRequest;
import com.myhealth.workout.WorkoutScheduleDtos.GeneratePlanRequest;
import com.myhealth.workout.WorkoutScheduleDtos.ScheduleDayDto;
import com.myhealth.workout.WorkoutScheduleDtos.WorkoutScheduleResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkoutScheduleService {
    private final WorkoutScheduleRepository schedules;
    private final WorkoutService workoutService;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public WorkoutScheduleService(WorkoutScheduleRepository schedules, WorkoutService workoutService,
                                  AiProvider aiProvider, ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate) {
        this.schedules = schedules;
        this.workoutService = workoutService;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Plan a fresh weekly split and persist it. The AI call runs OUTSIDE any transaction —
     * it can take tens of seconds and must not hold a DB connection; only the save below is
     * transactional.
     */
    public WorkoutScheduleResponse generate(AppUser user, GeneratePlanRequest request) {
        LocalDate startDate = request.startDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weeks = request.weeks();
        int daysPerWeek = request.daysPerWeek();
        WorkoutIntensity intensity = request.intensity() == null ? WorkoutIntensity.medium : request.intensity();
        String goal = goalLabel(user.getProfile());

        List<ScheduleDay> days = aiProvider.planWorkoutSchedule(goal, daysPerWeek, intensity.name());

        return transactionTemplate.execute(status -> {
            WorkoutSchedule schedule = new WorkoutSchedule();
            schedule.setUser(user);
            schedule.setGoal(goal);
            schedule.setStartDate(startDate);
            schedule.setWeeks(weeks);
            schedule.setDaysPerWeek(daysPerWeek);
            schedule.setIntensity(intensity.name());
            schedule.setDaysJson(JsonColumns.write(objectMapper, days));
            return toResponse(schedules.save(schedule));
        });
    }

    public List<WorkoutScheduleResponse> list(AppUser user) {
        return schedules.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkoutScheduleResponse get(AppUser user, Long id) {
        return toResponse(findOwned(user, id));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        schedules.delete(findOwned(user, id));
    }

    /**
     * Turn one weekday of a saved schedule into a real workout plan for {@code date}, by
     * running the normal AI generation for that day's category/duration at the schedule's
     * intensity. Rejects rest days and dates whose weekday doesn't match the requested one.
     */
    public WorkoutPlanResponse applyDay(AppUser user, Long scheduleId, ApplyDayRequest request) {
        WorkoutSchedule schedule = findOwned(user, scheduleId);
        int weekday = request.weekday();
        if (request.date().getDayOfWeek().getValue() != weekday) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                    "Selected date does not fall on the chosen weekday");
        }
        ScheduleDay day = readDays(schedule).stream()
                .filter(d -> d.weekday() == weekday)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "No such day in schedule"));
        if (day.rest() || day.category() == null || day.category().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                    "That day is a rest day — nothing to generate");
        }
        WorkoutCategory category = parseCategory(day.category());
        GenerateWorkoutRequest generate = new GenerateWorkoutRequest(
                request.date(), category, day.durationMin(),
                WorkoutIntensity.valueOf(schedule.getIntensity()), null);
        return workoutService.generate(user, generate);
    }

    private WorkoutCategory parseCategory(String code) {
        try {
            return WorkoutCategory.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                    "Unknown workout category in schedule");
        }
    }

    private WorkoutSchedule findOwned(AppUser user, Long id) {
        return schedules.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "Workout schedule not found"));
    }

    private WorkoutScheduleResponse toResponse(WorkoutSchedule schedule) {
        List<ScheduleDayDto> days = readDays(schedule).stream()
                .map(d -> new ScheduleDayDto(d.weekday(), d.rest(), d.category(), d.durationMin(), d.focus()))
                .toList();
        return new WorkoutScheduleResponse(
                schedule.getId(),
                schedule.getGoal(),
                schedule.getStartDate(),
                schedule.getWeeks(),
                schedule.getDaysPerWeek(),
                schedule.getIntensity(),
                days,
                schedule.getCreatedAt());
    }

    private List<ScheduleDay> readDays(WorkoutSchedule schedule) {
        return JsonColumns.read(objectMapper, schedule.getDaysJson(), new TypeReference<List<ScheduleDay>>() {
        });
    }

    private String goalLabel(Profile profile) {
        if (profile == null || profile.getGoal() == null) {
            return "維持健康";
        }
        return switch (profile.getGoal()) {
            case fat_loss -> "減脂";
            case muscle_gain -> "增肌";
            case maintain -> "維持";
        };
    }
}
