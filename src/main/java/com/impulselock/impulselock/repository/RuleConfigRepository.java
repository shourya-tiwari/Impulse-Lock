package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.RuleConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleConfigRepository extends JpaRepository<RuleConfig, Long> {

    Optional<RuleConfig> findByRuleCode(String ruleCode);
}
