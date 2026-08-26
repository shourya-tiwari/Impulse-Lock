package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.RestrictedCategory;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.support.RuleTestFixtures;
import org.junit.jupiter.api.Test;

class CategoryRestrictionRuleTest {

    private final CategoryRestrictionRule rule = new CategoryRestrictionRule();
    private final RuleConfig config = RuleTestFixtures.ruleConfig(CategoryRestrictionRule.RULE_CODE, 25.0);

    @Test
    void doesNotFireWhenUserHasNoRestrictedCategories() {
        assertThat(rule.evaluate(transactionInCategory("luxury"), new User(), RuleTestFixtures.contextWith(config)))
                .isZero();
    }

    @Test
    void firesWhenCategoryMatchesCaseInsensitively() {
        User user = new User();
        user.getRestrictedCategories().add(new RestrictedCategory(user, "Luxury"));

        assertThat(rule.evaluate(transactionInCategory("LUXURY"), user, RuleTestFixtures.contextWith(config)))
                .isEqualTo(25.0);
    }

    @Test
    void doesNotFireWhenCategoryIsNotRestricted() {
        User user = new User();
        user.getRestrictedCategories().add(new RestrictedCategory(user, "gaming"));

        assertThat(rule.evaluate(transactionInCategory("groceries"), user, RuleTestFixtures.contextWith(config)))
                .isZero();
    }

    @Test
    void doesNotFireWhenTransactionHasNoCategory() {
        assertThat(rule.evaluate(new Transaction(), new User(), RuleTestFixtures.contextWith(config))).isZero();
    }

    private Transaction transactionInCategory(String category) {
        Transaction transaction = new Transaction();
        transaction.setCategory(category);
        return transaction;
    }
}
