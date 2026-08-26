package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.DecisionThresholds;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionThresholdsRepository extends JpaRepository<DecisionThresholds, Long> {

    Optional<DecisionThresholds> findTopByOrderByIdAsc();
}
