package com.myhealth.goal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightGoalRepository extends JpaRepository<WeightGoal, Long> {
    Optional<WeightGoal> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
