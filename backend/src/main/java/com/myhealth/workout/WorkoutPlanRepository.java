package com.myhealth.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {
    List<WorkoutPlan> findByUserIdAndDateOrderByCreatedAtDesc(Long userId, LocalDate date);

    List<WorkoutPlan> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<WorkoutPlan> findByIdAndUserId(Long id, Long userId);

    List<WorkoutPlan> findByUserIdOrderByDateAscCreatedAtAsc(Long userId);

    int countByUserIdAndDate(Long userId, LocalDate date);

    long countByUserIdAndDoneTrue(Long userId);

    /** Distinct days with a completed workout, for streak computation (date column only). */
    @Query("select distinct w.date from WorkoutPlan w "
            + "where w.user.id = :userId and w.done = true and w.date between :from and :to")
    List<LocalDate> findDistinctDoneWorkoutDates(@Param("userId") Long userId,
                                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** All completed-workout dates (with duplicates) for per-week aggregation in achievements. */
    @Query("select w.date from WorkoutPlan w "
            + "where w.user.id = :userId and w.done = true and w.date between :from and :to")
    List<LocalDate> findDoneWorkoutDates(@Param("userId") Long userId,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Completed workout plans in the lookback window, used for schedule-adherence achievements. */
    @Query("select w from WorkoutPlan w "
            + "where w.user.id = :userId and w.done = true and w.date between :from and :to "
            + "order by w.date asc, w.createdAt asc")
    List<WorkoutPlan> findDoneWorkouts(@Param("userId") Long userId,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    int countByUserIdAndDateAndDoneTrue(Long userId, LocalDate date);

    int countByUserIdAndDateBetweenAndDoneTrue(Long userId, LocalDate from, LocalDate to);

    /** Keyword search over a plan's category and exercise items ({@code :q} is a lowercased %like%). */
    @Query("select w from WorkoutPlan w where w.user.id = :userId and ("
            + "lower(w.category) like :q or lower(cast(w.itemsJson as string)) like :q)")
    List<WorkoutPlan> search(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);
}
