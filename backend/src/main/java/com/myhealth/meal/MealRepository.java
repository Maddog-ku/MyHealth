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

    long countByUserId(Long userId);

    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    /** Distinct days that have at least one meal, for streak computation (date column only). */
    @Query("select distinct m.date from Meal m where m.user.id = :userId and m.date between :from and :to")
    List<LocalDate> findDistinctMealDates(@Param("userId") Long userId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select m.imageUrl from Meal m where m.user.id = :userId and m.imageUrl is not null and m.imageUrl <> ''")
    List<String> findImageUrlsByUserId(@Param("userId") Long userId);
}
