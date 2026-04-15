package com.impulselock.impulselock;

import com.impulselock.impulselock.engine.DecisionEngine;
import com.impulselock.impulselock.model.*;
import com.impulselock.impulselock.rules.*;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
public class ImpulseLockApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImpulseLockApplication.class, args);

        UserProfile user = new UserProfile();
        user.setUserId("U101");
        user.setDailyLimit(2000);
        user.setNightSpendingAllowed(false);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("T9001");
        transaction.setUserId("U101");
        transaction.setAmount(3500);
        transaction.setTimestamp(LocalDateTime.now());

        DecisionEngine engine = new DecisionEngine();
        Decision decision = engine.evaluate(
                transaction,
                user,
                List.of(
                    new HighAmountRule(),
                    new NightSpendingRule(),
                    new FrequentTransactionRule(),
                    new CategoryRestrictionRule(),
                    new SensitivityLevelRule()
                )
        );

        System.out.println("Decision: " + decision.getDecisionType());
        System.out.println("Risk Score: " + decision.getRiskScore());
        System.out.println("Reason: " + decision.getExplanation());
    }
}
