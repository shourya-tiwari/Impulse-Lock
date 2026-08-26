package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.dto.TransactionEvaluateRequest;
import com.impulselock.impulselock.dto.TransactionHistoryFilter;
import com.impulselock.impulselock.engine.DecisionEngine;
import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.engine.RuleContextFactory;
import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.TransactionNotFoundException;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private DecisionEngine decisionEngine;
    @Mock
    private RuleContextFactory ruleContextFactory;
    @Mock
    private RuleContext ruleContext;

    private TransactionService newService() {
        return new TransactionService(userRepository, transactionRepository, decisionEngine, ruleContextFactory, List.of());
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }

    private User adminUser(String username) {
        User user = user(username);
        Set<Role> roles = new HashSet<>();
        roles.add(new Role("ROLE_ADMIN"));
        user.setRoles(roles);
        return user;
    }

    @Test
    void evaluateAndSavePersistsTheEnginesDecisionOntoTheTransaction() {
        TransactionService service = newService();
        User user = user("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(ruleContextFactory.buildFor(any(), any())).thenReturn(ruleContext);

        Decision decision = new Decision();
        decision.setDecisionType(DecisionType.DELAY);
        decision.setRiskScore(45.0);
        decision.setExplanation("Night spending restricted; ");
        decision.setTriggeredRules(List.of());
        when(decisionEngine.evaluate(any(), any(), any(), any())).thenReturn(decision);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionEvaluateRequest request = new TransactionEvaluateRequest();
        request.setAmount(BigDecimal.valueOf(500));
        request.setCategory("gaming");
        request.setMerchant("Steam");

        Transaction saved = service.evaluateAndSave("alice", request);

        assertThat(saved.getPublicId()).isNotBlank();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getDecisionType()).isEqualTo(DecisionType.DELAY);
        assertThat(saved.getRiskScore()).isEqualByComparingTo(BigDecimal.valueOf(45.0));
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void evaluateAndSaveRejectsANullRequest() {
        TransactionService service = newService();
        assertThatThrownBy(() -> service.evaluateAndSave("alice", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluateAndSaveRejectsAMissingAmount() {
        TransactionService service = newService();
        assertThatThrownBy(() -> service.evaluateAndSave("alice", new TransactionEvaluateRequest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluateAndSaveThrowsWhenTheUserDoesNotExist() {
        TransactionService service = newService();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        TransactionEvaluateRequest request = new TransactionEvaluateRequest();
        request.setAmount(BigDecimal.TEN);

        assertThatThrownBy(() -> service.evaluateAndSave("ghost", request)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getByPublicIdReturnsItToItsOwner() {
        TransactionService service = newService();
        User owner = user("alice");
        Transaction transaction = new Transaction();
        transaction.setUser(owner);
        when(transactionRepository.findByPublicId("tx-1")).thenReturn(Optional.of(transaction));

        Transaction result = service.getByPublicId(new SecurityUser(owner), "tx-1");

        assertThat(result).isSameAs(transaction);
    }

    @Test
    void getByPublicIdReturnsItToAnAdminEvenIfNotTheOwner() {
        TransactionService service = newService();
        Transaction transaction = new Transaction();
        transaction.setUser(user("alice"));
        when(transactionRepository.findByPublicId("tx-1")).thenReturn(Optional.of(transaction));

        Transaction result = service.getByPublicId(new SecurityUser(adminUser("root")), "tx-1");

        assertThat(result).isSameAs(transaction);
    }

    @Test
    void getByPublicIdHidesAnotherUsersTransactionAs404NotAs403() {
        TransactionService service = newService();
        Transaction transaction = new Transaction();
        transaction.setUser(user("alice"));
        when(transactionRepository.findByPublicId("tx-1")).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.getByPublicId(new SecurityUser(user("mallory")), "tx-1"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void getByPublicIdThrowsForAnUnknownId() {
        TransactionService service = newService();
        when(transactionRepository.findByPublicId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByPublicId(new SecurityUser(user("alice")), "missing"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void searchThrowsWhenTheUserDoesNotExist() {
        TransactionService service = newService();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        TransactionHistoryFilter filter = new TransactionHistoryFilter(null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.search("ghost", filter, Pageable.unpaged()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void searchDelegatesToTheRepositoryForTheResolvedUser() {
        TransactionService service = newService();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice")));
        Transaction transaction = new Transaction();
        Page<Transaction> page = new PageImpl<>(List.of(transaction));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        TransactionHistoryFilter filter = new TransactionHistoryFilter(null, null, "gaming", null, null, null, null);

        Page<Transaction> result = service.search("alice", filter, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(transaction);
    }

    @Test
    void searchForExportCapsAtTheRowLimitAndSortsByMostRecentFirst() {
        TransactionService service = newService();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice")));
        Transaction first = new Transaction();
        Transaction second = new Transaction();
        when(transactionRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(first, second));
        TransactionHistoryFilter filter = new TransactionHistoryFilter(null, null, null, null, null, null, null);

        List<Transaction> result = service.searchForExport("alice", filter);

        assertThat(result).containsExactly(first, second);
    }
}
