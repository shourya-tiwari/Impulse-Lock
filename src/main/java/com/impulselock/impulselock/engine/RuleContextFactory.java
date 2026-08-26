package com.impulselock.impulselock.engine;

import com.impulselock.impulselock.entity.DecisionThresholds;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.DecisionThresholdsRepository;
import com.impulselock.impulselock.repository.RuleConfigRepository;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.TransactionSpecifications;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link RuleContext} each transaction evaluation needs: current rule
 * weights/enabled/params, the global decision thresholds, and a 24-hour lookback of the user's
 * own transactions - one query, reused by every rule that needs history (HighAmountRule filters
 * it down to "today"; FrequentTransactionRule filters it down to its own configurable velocity
 * window).
 *
 * <p>24 hours is a deliberate fixed bound: it always fully covers a calendar day (see
 * {@code HighAmountRule}) and comfortably covers any sane {@code velocityWindowMinutes}. A rule
 * config with a velocity window longer than 24h would silently under-count - an accepted
 * limitation at this project's scale rather than something worth a second, wider query for.
 */
@Component
public class RuleContextFactory {

    private static final long LOOKBACK_HOURS = 24;

    private final RuleConfigRepository ruleConfigRepository;
    private final DecisionThresholdsRepository decisionThresholdsRepository;
    private final TransactionRepository transactionRepository;

    public RuleContextFactory(RuleConfigRepository ruleConfigRepository,
                               DecisionThresholdsRepository decisionThresholdsRepository,
                               TransactionRepository transactionRepository) {
        this.ruleConfigRepository = ruleConfigRepository;
        this.decisionThresholdsRepository = decisionThresholdsRepository;
        this.transactionRepository = transactionRepository;
    }

    public RuleContext buildFor(User user, LocalDateTime asOf) {
        Map<String, RuleConfig> configsByCode = ruleConfigRepository.findAll().stream()
                .collect(Collectors.toMap(RuleConfig::getRuleCode, Function.identity()));

        DecisionThresholds thresholds = decisionThresholdsRepository.findTopByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "decision_thresholds has no row - did the seed migration run?"));

        List<Transaction> recentTransactions = transactionRepository.findAll(
                TransactionSpecifications.byUser(user)
                        .and(TransactionSpecifications.occurredBetween(asOf.minusHours(LOOKBACK_HOURS), asOf)));

        return new RuleContext(configsByCode, recentTransactions, thresholds);
    }
}
