package com.myhealth.workout;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutScheduleRepository extends JpaRepository<WorkoutSchedule, Long> {
    List<WorkoutSchedule> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WorkoutSchedule> findByIdAndUserId(Long id, Long userId);
}
