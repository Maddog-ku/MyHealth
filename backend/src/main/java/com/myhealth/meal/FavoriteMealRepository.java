package com.myhealth.meal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteMealRepository extends JpaRepository<FavoriteMeal, Long> {
    List<FavoriteMeal> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteMeal> findByIdAndUserId(Long id, Long userId);
}
