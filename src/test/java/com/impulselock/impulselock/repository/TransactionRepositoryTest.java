package com.impulselock.impulselock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void savesAndFindsByPublicId() {
        User user = persistUser("frank");
        Transaction transaction = newTransaction(user, BigDecimal.valueOf(500), "groceries", LocalDateTime.now());
        transactionRepository.saveAndFlush(transaction);

        assertThat(transactionRepository.findByPublicId(transaction.getPublicId())).isPresent();
    }

    @Test
    void filtersByUserCategoryAndAmountRangeViaSpecifications() {
        User user = persistUser("grace");
        User other = persistUser("henry");

        transactionRepository.saveAndFlush(
                newTransaction(user, BigDecimal.valueOf(50), "groceries", LocalDateTime.now().minusDays(1)));
        transactionRepository.saveAndFlush(
                newTransaction(user, BigDecimal.valueOf(1500), "luxury", LocalDateTime.now()));
        transactionRepository.saveAndFlush(
                newTransaction(other, BigDecimal.valueOf(1500), "luxury", LocalDateTime.now()));

        Specification<Transaction> spec = TransactionSpecifications.byUser(user)
                .and(TransactionSpecifications.byCategory("luxury"))
                .and(TransactionSpecifications.amountBetween(BigDecimal.valueOf(1000), BigDecimal.valueOf(2000)));

        List<Transaction> results = transactionRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUser().getUsername()).isEqualTo("grace");
    }

    @Test
    void filtersByOccurredAtRange() {
        User user = persistUser("iris");
        LocalDateTime now = LocalDateTime.now();

        transactionRepository.saveAndFlush(newTransaction(user, BigDecimal.TEN, "misc", now.minusDays(10)));
        transactionRepository.saveAndFlush(newTransaction(user, BigDecimal.TEN, "misc", now));

        Specification<Transaction> spec = TransactionSpecifications.byUser(user)
                .and(TransactionSpecifications.occurredBetween(now.minusDays(1), now.plusDays(1)));

        assertThat(transactionRepository.findAll(spec)).hasSize(1);
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hashed");
        user.setDailyLimit(BigDecimal.valueOf(2000));
        user.setSensitivityLevel(5);
        return userRepository.saveAndFlush(user);
    }

    private Transaction newTransaction(User user, BigDecimal amount, String category, LocalDateTime occurredAt) {
        Transaction transaction = new Transaction();
        transaction.setPublicId(UUID.randomUUID().toString());
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setMerchant("TestMerchant");
        transaction.setOccurredAt(occurredAt);
        return transaction;
    }
}
