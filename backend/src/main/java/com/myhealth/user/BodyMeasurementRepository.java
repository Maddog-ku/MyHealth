package com.myhealth.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {
    List<BodyMeasurement> findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(Long userId, Instant from, Instant to);

    Optional<BodyMeasurement> findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(Long userId, Instant measuredAt);
}
