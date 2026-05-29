package com.myhealth.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {
    List<WorkoutPlan> findByUserIdAndDateOrderByCreatedAtDesc(Long userId, LocalDate date);

    List<WorkoutPlan> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<WorkoutPlan> findByIdAndUserId(Long id, Long userId);

    int countByUserIdAndDate(Long userId, LocalDate date);

    int countByUserIdAndDateAndDoneTrue(Long userId, LocalDate date);
}
