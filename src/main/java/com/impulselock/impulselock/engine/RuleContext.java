package com.impulselock.impulselock.engine;

import com.impulselock.impulselock.entity.DecisionThresholds;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import java.util.List;
import java.util.Map;

/**
 * Everything a {@code SpendingRule} needs beyond the transaction/user being evaluated: current
 * weights/enabled-flags/params per rule, the global decision thresholds, and a lookback window
 * of the user's own transactions (rules filter this themselves to whatever window they actually
 * need - see {@code RuleContextFactory}'s javadoc for why one shared 24h fetch covers every
 * current rule). Built fresh per evaluation, not cached - rule config changes take effect on the
 * very next transaction.
 */
public class RuleContext {

    private final Map<String, RuleConfig> configsByCode;
    private final List<Transaction> recentTransactions;
    private final DecisionThresholds thresholds;

    public RuleContext(Map<String, RuleConfig> configsByCode, List<Transaction> recentTransactions,
                        DecisionThresholds thresholds) {
        this.configsByCode = configsByCode;
        this.recentTransactions = recentTransactions;
        this.thresholds = thresholds;
    }

    public RuleConfig configFor(String ruleCode) {
        RuleConfig config = configsByCode.get(ruleCode);
        if (config == null) {
            throw new IllegalStateException(
                    "No RuleConfig found for rule code: " + ruleCode + " - did the seed migration run?");
        }
        return config;
    }

    public double weightFor(String ruleCode) {
        return configFor(ruleCode).getWeight().doubleValue();
    }

    public boolean isEnabled(String ruleCode) {
        return configFor(ruleCode).isEnabled();
    }

    public Map<String, Object> paramsFor(String ruleCode) {
        return configFor(ruleCode).getParams();
    }

    public List<Transaction> getRecentTransactions() {
        return recentTransactions;
    }

    public DecisionThresholds getThresholds() {
        return thresholds;
    }
}
