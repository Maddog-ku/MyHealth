package com.myhealth.report;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {
    Optional<WeeklyReport> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
