package com.myhealth.meal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByUserIdAndDateOrderByCreatedAtDesc(Long userId, LocalDate date);

    List<Meal> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<Meal> findByIdAndUserId(Long id, Long userId);
}
