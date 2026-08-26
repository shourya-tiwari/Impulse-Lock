package com.impulselock.impulselock.service;

import com.impulselock.impulselock.dto.TransactionEvaluateRequest;
import com.impulselock.impulselock.engine.DecisionEngine;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.DatabaseOperationException;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.rules.SpendingRule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-pointed at the JPA entities/repositories introduced in Phase 0 (see docs/v2/tasks.md,
 * Phase 0). {@code request.username} is a transitional stand-in for the authenticated caller
 * identity - Phase 1 removes it and resolves the acting user from the security context instead
 * (see docs/v2/security-design.md).
 */
@Service
public class TransactionService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final DecisionEngine decisionEngine;
    private final List<SpendingRule> rules;

    public TransactionService(UserRepository userRepository,
                              TransactionRepository transactionRepository,
                              DecisionEngine decisionEngine,
                              List<SpendingRule> rules) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.decisionEngine = decisionEngine;
        this.rules = rules;
    }

    @Transactional
    public Decision evaluateAndSave(TransactionEvaluateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Transaction request body is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("amount is required");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found for username: " + request.getUsername()));

        Transaction transaction = new Transaction();
        transaction.setPublicId(UUID.randomUUID().toString());
        transaction.setUser(user);
        transaction.setAmount(request.getAmount());
        transaction.setCategory(request.getCategory());
        transaction.setMerchant(request.getMerchant());
        transaction.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now());

        Decision decision = decisionEngine.evaluate(transaction, user, rules);

        try {
            transactionRepository.save(transaction);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save transaction in database", exception);
        }

        return decision;
    }
}
