package com.myhealth.meal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByUserIdAndDateOrderByCreatedAtDesc(Long userId, LocalDate date);

    List<Meal> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<Meal> findByIdAndUserId(Long id, Long userId);

    @Query("select m.imageUrl from Meal m where m.user.id = :userId and m.imageUrl is not null and m.imageUrl <> ''")
    List<String> findImageUrlsByUserId(@Param("userId") Long userId);
}
