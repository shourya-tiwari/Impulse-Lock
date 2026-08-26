package com.impulselock.impulselock.service;

import com.impulselock.impulselock.dto.TransactionEvaluateRequest;
import com.impulselock.impulselock.dto.TransactionHistoryFilter;
import com.impulselock.impulselock.engine.DecisionEngine;
import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.engine.RuleContextFactory;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.DatabaseOperationException;
import com.impulselock.impulselock.exception.TransactionNotFoundException;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.TransactionSpecifications;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.rules.SpendingRule;
import com.impulselock.impulselock.security.SecurityUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code username} comes from the authenticated JWT principal (see {@code TransactionController}),
 * never the request body - closes docs/v1/design-decisions.md item 7. As of Phase 2, the
 * computed {@link Decision} is also persisted onto the {@code Transaction} row (decisionType,
 * riskScore, explanation, triggeredRules) - V1/Phase 0/1 only ever returned it to the caller
 * (see docs/v1/database.md). As of Phase 3, {@link #evaluateAndSave} returns the saved
 * {@code Transaction} entity itself (mapped to a DTO by the controller) rather than a bare
 * {@code Decision}, since {@code GET /transactions/{publicId}} and history rows need the same
 * shape - one entity now serves all three call sites.
 */
@Service
public class TransactionService {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final DecisionEngine decisionEngine;
    private final RuleContextFactory ruleContextFactory;
    private final List<SpendingRule> rules;

    public TransactionService(UserRepository userRepository,
                              TransactionRepository transactionRepository,
                              DecisionEngine decisionEngine,
                              RuleContextFactory ruleContextFactory,
                              List<SpendingRule> rules) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.decisionEngine = decisionEngine;
        this.ruleContextFactory = ruleContextFactory;
        this.rules = rules;
    }

    @Transactional
    public Transaction evaluateAndSave(String username, TransactionEvaluateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Transaction request body is required");
        }
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("amount is required");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for username: " + username));

        Transaction transaction = new Transaction();
        transaction.setPublicId(UUID.randomUUID().toString());
        transaction.setUser(user);
        transaction.setAmount(request.getAmount());
        transaction.setCategory(request.getCategory());
        transaction.setMerchant(request.getMerchant());
        transaction.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now());

        RuleContext context = ruleContextFactory.buildFor(user, transaction.getOccurredAt());
        Decision decision = decisionEngine.evaluate(transaction, user, rules, context);

        transaction.setDecisionType(decision.getDecisionType());
        transaction.setRiskScore(BigDecimal.valueOf(decision.getRiskScore()));
        transaction.setExplanation(decision.getExplanation());
        transaction.setTriggeredRules(decision.getTriggeredRules());

        try {
            transactionRepository.save(transaction);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save transaction in database", exception);
        }

        return transaction;
    }

    /**
     * A non-owner gets the same {@link TransactionNotFoundException} (404) an actually-missing
     * {@code publicId} would - never a 403 - so the response never confirms another user's
     * transaction exists (see docs/v2/api-design.md#error-format). {@code ROLE_ADMIN} may view
     * any transaction.
     */
    @Transactional(readOnly = true)
    public Transaction getByPublicId(SecurityUser principal, String publicId) {
        Transaction transaction = transactionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new TransactionNotFoundException(publicId));

        boolean isOwner = transaction.getUser().getUsername().equals(principal.getUsername());
        if (!isOwner && !principal.isAdmin()) {
            throw new TransactionNotFoundException(publicId);
        }
        return transaction;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> search(String username, TransactionHistoryFilter filter, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for username: " + username));

        return transactionRepository.findAll(buildSpecification(user, filter), pageable);
    }

    /** Row-capped, unpaginated - backs the CSV export (see TransactionController). */
    @Transactional(readOnly = true)
    public List<Transaction> searchForExport(String username, TransactionHistoryFilter filter) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for username: " + username));

        Sort sort = Sort.by(Sort.Direction.DESC, "occurredAt");
        return transactionRepository.findAll(buildSpecification(user, filter), sort).stream()
                .limit(MAX_EXPORT_ROWS)
                .collect(Collectors.toList());
    }

    private Specification<Transaction> buildSpecification(User user, TransactionHistoryFilter filter) {
        Specification<Transaction> spec = TransactionSpecifications.byUser(user);

        if (filter.from() != null || filter.to() != null) {
            spec = spec.and(TransactionSpecifications.occurredBetween(filter.from(), filter.to()));
        }
        if (filter.category() != null) {
            spec = spec.and(TransactionSpecifications.byCategory(filter.category()));
        }
        if (filter.merchant() != null) {
            spec = spec.and(TransactionSpecifications.byMerchant(filter.merchant()));
        }
        if (filter.decisionType() != null) {
            spec = spec.and(TransactionSpecifications.byDecisionType(filter.decisionType()));
        }
        if (filter.minAmount() != null || filter.maxAmount() != null) {
            spec = spec.and(TransactionSpecifications.amountBetween(filter.minAmount(), filter.maxAmount()));
        }
        return spec;
    }
}
