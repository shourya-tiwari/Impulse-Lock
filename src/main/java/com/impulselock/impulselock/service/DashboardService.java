package com.impulselock.impulselock.service;

import com.impulselock.impulselock.dto.DashboardSummaryResponse;
import com.impulselock.impulselock.dto.RiskTrendPointResponse;
import com.impulselock.impulselock.dto.SpendingByCategoryResponse;
import com.impulselock.impulselock.dto.TopTriggeredRuleResponse;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.model.TriggeredRuleEntry;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.TransactionSpecifications;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method is scoped to the authenticated caller by default; passing {@code targetUserId}
 * lets a {@code ROLE_ADMIN} caller view another user's dashboard (see
 * docs/v2/api-design.md#dashboard-endpoints-apiv2dashboard--authenticated-new-in-v2).
 * <b>Deviation</b>: that api-design.md section calls the admin override "audit-logged as an
 * admin cross-user view" - the audit writer doesn't exist until Phase 4, so this override works
 * but isn't logged yet (see docs/v2/tasks.md, Phase 3's note).
 *
 * <p>Aggregates are computed in-memory over one fetched 30-day transaction list per call rather
 * than via separate SQL aggregate queries - consistent with how {@code RuleContextFactory}/rules
 * already do bounded in-memory computation, and simplest at this project's data scale.
 */
@Service
public class DashboardService {

    private static final int PERIOD_DAYS = 30;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public DashboardService(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(SecurityUser principal, Long targetUserId) {
        List<Transaction> transactions = recentTransactionsFor(principal, targetUserId);

        BigDecimal totalSpend = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long allow = countByDecision(transactions, DecisionType.ALLOW);
        long delay = countByDecision(transactions, DecisionType.DELAY);
        long block = countByDecision(transactions, DecisionType.BLOCK);
        double averageRisk = transactions.stream()
                .mapToDouble(t -> t.getRiskScore().doubleValue())
                .average()
                .orElse(0);

        return new DashboardSummaryResponse(transactions.size(), totalSpend, allow, delay, block, averageRisk);
    }

    @Transactional(readOnly = true)
    public List<SpendingByCategoryResponse> spendingByCategory(SecurityUser principal, Long targetUserId) {
        List<Transaction> transactions = recentTransactionsFor(principal, targetUserId);

        Map<String, List<Transaction>> byCategory = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getCategory() != null ? t.getCategory() : "uncategorized"));

        return byCategory.entrySet().stream()
                .map(entry -> new SpendingByCategoryResponse(
                        entry.getKey(),
                        entry.getValue().stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().size()))
                .sorted(Comparator.comparing(SpendingByCategoryResponse::getTotalAmount).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RiskTrendPointResponse> riskTrend(SecurityUser principal, Long targetUserId) {
        List<Transaction> transactions = recentTransactionsFor(principal, targetUserId);

        Map<LocalDate, List<Transaction>> byDate = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getOccurredAt().toLocalDate()));

        return byDate.entrySet().stream()
                .map(entry -> new RiskTrendPointResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToDouble(t -> t.getRiskScore().doubleValue()).average().orElse(0)))
                .sorted(Comparator.comparing(RiskTrendPointResponse::getDate))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TopTriggeredRuleResponse> topTriggeredRules(SecurityUser principal, Long targetUserId) {
        List<Transaction> transactions = recentTransactionsFor(principal, targetUserId);

        Map<String, Long> counts = transactions.stream()
                .flatMap(t -> t.getTriggeredRules().stream())
                .collect(Collectors.groupingBy(TriggeredRuleEntry::ruleCode, Collectors.counting()));

        return counts.entrySet().stream()
                .map(entry -> new TopTriggeredRuleResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(TopTriggeredRuleResponse::getTriggerCount).reversed())
                .collect(Collectors.toList());
    }

    private List<Transaction> recentTransactionsFor(SecurityUser principal, Long targetUserId) {
        User user = resolveTargetUser(principal, targetUserId);
        LocalDateTime now = LocalDateTime.now();

        return transactionRepository.findAll(
                TransactionSpecifications.byUser(user)
                        .and(TransactionSpecifications.occurredBetween(now.minusDays(PERIOD_DAYS), now)));
    }

    private User resolveTargetUser(SecurityUser principal, Long targetUserId) {
        if (targetUserId == null) {
            return userRepository.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new UserNotFoundException("User not found for username: " + principal.getUsername()));
        }
        if (!principal.isAdmin()) {
            throw new AccessDeniedException("Only admins may view another user's dashboard");
        }
        return userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + targetUserId));
    }

    private long countByDecision(List<Transaction> transactions, DecisionType type) {
        return transactions.stream().filter(t -> t.getDecisionType() == type).count();
    }
}
