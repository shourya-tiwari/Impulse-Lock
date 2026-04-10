package com.impulselock.impulselock.service;

import com.impulselock.impulselock.engine.DecisionEngine;
import com.impulselock.impulselock.exception.DatabaseOperationException;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.rules.SpendingRule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

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

    public Decision evaluateAndSave(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction request body is required");
        }
        if (transaction.getUserId() == null || transaction.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        UserProfile userProfile = userRepository.getUserById(transaction.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found for userId: " + transaction.getUserId()));

        if (transaction.getTransactionId() == null || transaction.getTransactionId().isBlank()) {
            transaction.setTransactionId(UUID.randomUUID().toString());
        }
        if (transaction.getTimestamp() == null) {
            transaction.setTimestamp(LocalDateTime.now());
        }

        Decision decision = decisionEngine.evaluate(transaction, userProfile, rules);

        try {
            transactionRepository.saveTransaction(transaction);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save transaction in database", exception);
        }

        return decision;
    }
}
