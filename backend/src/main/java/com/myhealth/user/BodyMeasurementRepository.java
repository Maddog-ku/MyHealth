package com.myhealth.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {
    List<BodyMeasurement> findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(Long userId, Instant from, Instant to);

    Optional<BodyMeasurement> findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(Long userId, Instant measuredAt);

    List<BodyMeasurement> findByUserIdOrderByMeasuredAtAsc(Long userId);

    long countByUserId(Long userId);

    /** Just the timestamps in range, for streak computation (no need to hydrate full rows). */
    @Query("select m.measuredAt from BodyMeasurement m where m.user.id = :userId and m.measuredAt between :from and :to")
    List<Instant> findMeasuredAtBetween(@Param("userId") Long userId,
                                        @Param("from") Instant from, @Param("to") Instant to);
}
