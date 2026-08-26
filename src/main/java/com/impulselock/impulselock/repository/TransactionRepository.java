package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.Transaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Replaces V1's hand-written JdbcTemplate repository of the same name (see docs/v1/database.md).
 * {@link JpaSpecificationExecutor} backs the dynamic transaction-history filtering built with
 * {@link TransactionSpecifications} (wired up to an actual endpoint in Phase 3).
 */
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByPublicId(String publicId);
}
