package com.myhealth.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {
    List<HabitLog> findByUserIdAndDate(Long userId, LocalDate date);

    /** All habit logs in [from, to] — used to derive the current per-habit streak. */
    List<HabitLog> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    List<HabitLog> findByUserIdOrderByDateAscTypeAsc(Long userId);

    Optional<HabitLog> findByUserIdAndDateAndType(Long userId, LocalDate date, HabitType type);
}
