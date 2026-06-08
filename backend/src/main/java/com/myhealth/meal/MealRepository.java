package com.myhealth.meal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByUserIdAndDateOrderByCreatedAtDesc(Long userId, LocalDate date);

    List<Meal> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<Meal> findByIdAndUserId(Long id, Long userId);

    List<Meal> findByUserIdOrderByDateAscCreatedAtAsc(Long userId);

    long countByUserId(Long userId);

    /** Total meals logged with a (non-empty) photo, for the photo-meal achievement. */
    @Query("select count(m) from Meal m where m.user.id = :userId and m.imageUrl is not null and m.imageUrl <> ''")
    long countPhotoMeals(@Param("userId") Long userId);

    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    /** Distinct days that have at least one meal, for streak computation (date column only). */
    @Query("select distinct m.date from Meal m where m.user.id = :userId and m.date between :from and :to")
    List<LocalDate> findDistinctMealDates(@Param("userId") Long userId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select m.imageUrl from Meal m where m.user.id = :userId and m.imageUrl is not null and m.imageUrl <> ''")
    List<String> findImageUrlsByUserId(@Param("userId") Long userId);

    @Query("select m from Meal m where m.user.id = :userId and m.date < :beforeDate order by m.date desc, m.createdAt desc")
    List<Meal> findRecentBeforeDate(@Param("userId") Long userId,
                                    @Param("beforeDate") LocalDate beforeDate,
                                    Pageable pageable);

    /** Keyword search over a meal's description, food items and slot ({@code :q} is a lowercased %like%). */
    @Query("select m from Meal m where m.user.id = :userId and ("
            + "lower(m.description) like :q or lower(cast(m.itemsJson as string)) like :q or lower(m.slot) like :q)")
    List<Meal> search(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);
}
