package com.myhealth.workout;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutGoalRepository extends JpaRepository<WorkoutGoal, Long> {
    Optional<WorkoutGoal> findByUserId(Long userId);
}
