package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.dto.DashboardSummaryResponse;
import com.impulselock.impulselock.dto.SpendingByCategoryResponse;
import com.impulselock.impulselock.dto.TopTriggeredRuleResponse;
import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.model.TriggeredRuleEntry;
import com.impulselock.impulselock.repository.TransactionRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditLogService auditLogService;

    private DashboardService newService() {
        return new DashboardService(userRepository, transactionRepository, auditLogService);
    }

    private User user(String username, Long id) {
        User user = new User();
        user.setUsername(username);
        return user;
    }

    private User adminUser(String username) {
        User user = user(username, 1L);
        Set<Role> roles = new HashSet<>();
        roles.add(new Role("ROLE_ADMIN"));
        user.setRoles(roles);
        return user;
    }

    private Transaction transaction(BigDecimal amount, DecisionType type, double riskScore, String category,
                                     LocalDateTime occurredAt, List<TriggeredRuleEntry> triggeredRules) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setDecisionType(type);
        transaction.setRiskScore(BigDecimal.valueOf(riskScore));
        transaction.setCategory(category);
        transaction.setOccurredAt(occurredAt);
        transaction.setTriggeredRules(triggeredRules);
        return transaction;
    }

    @Test
    void summaryAggregatesCountsSpendAndAverageRisk() {
        DashboardService service = newService();
        User alice = user("alice", 1L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        LocalDateTime now = LocalDateTime.now();
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(
                transaction(BigDecimal.valueOf(100), DecisionType.ALLOW, 10, "groceries", now, List.of()),
                transaction(BigDecimal.valueOf(200), DecisionType.BLOCK, 90, "luxury", now, List.of())));

        DashboardSummaryResponse summary = service.summary(new SecurityUser(alice), null);

        assertThat(summary.getTransactionCount()).isEqualTo(2);
        assertThat(summary.getTotalSpend()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(summary.getAllowCount()).isEqualTo(1);
        assertThat(summary.getBlockCount()).isEqualTo(1);
        assertThat(summary.getDelayCount()).isEqualTo(0);
        assertThat(summary.getAverageRiskScore()).isEqualTo(50.0);
    }

    @Test
    void summaryWithNoTransactionsAveragesToZeroNotNaN() {
        DashboardService service = newService();
        User alice = user("alice", 1L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        DashboardSummaryResponse summary = service.summary(new SecurityUser(alice), null);

        assertThat(summary.getTransactionCount()).isZero();
        assertThat(summary.getAverageRiskScore()).isZero();
    }

    @Test
    void spendingByCategoryGroupsAndSortsDescending() {
        DashboardService service = newService();
        User alice = user("alice", 1L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        LocalDateTime now = LocalDateTime.now();
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(
                transaction(BigDecimal.valueOf(50), DecisionType.ALLOW, 5, "groceries", now, List.of()),
                transaction(BigDecimal.valueOf(300), DecisionType.ALLOW, 5, "luxury", now, List.of())));

        List<SpendingByCategoryResponse> result = service.spendingByCategory(new SecurityUser(alice), null);

        assertThat(result).extracting(SpendingByCategoryResponse::getCategory).containsExactly("luxury", "groceries");
    }

    @Test
    void topTriggeredRulesCountsAcrossTransactions() {
        DashboardService service = newService();
        User alice = user("alice", 1L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        LocalDateTime now = LocalDateTime.now();
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(
                transaction(BigDecimal.TEN, DecisionType.DELAY, 40, "gaming", now,
                        List.of(new TriggeredRuleEntry("HIGH_AMOUNT", BigDecimal.valueOf(40), "over limit"))),
                transaction(BigDecimal.TEN, DecisionType.DELAY, 40, "gaming", now,
                        List.of(new TriggeredRuleEntry("HIGH_AMOUNT", BigDecimal.valueOf(40), "over limit"),
                                new TriggeredRuleEntry("NIGHT_SPENDING", BigDecimal.valueOf(30), "night")))));

        List<TopTriggeredRuleResponse> result = service.topTriggeredRules(new SecurityUser(alice), null);

        assertThat(result).extracting(TopTriggeredRuleResponse::getRuleCode).containsExactly("HIGH_AMOUNT", "NIGHT_SPENDING");
        assertThat(result.get(0).getTriggerCount()).isEqualTo(2);
    }

    @Test
    void aNonAdminCannotViewAnotherUsersDashboard() {
        DashboardService service = newService();
        User alice = user("alice", 1L);

        assertThatThrownBy(() -> service.summary(new SecurityUser(alice), 99L))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditLogService, never()).record(any(), any(), any(), any());
    }

    @Test
    void anAdminViewingAnotherUsersDashboardIsAuditLogged() {
        DashboardService service = newService();
        User admin = adminUser("root");
        User target = user("bob", 42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        service.summary(new SecurityUser(admin), 42L);

        verify(auditLogService).record("ADMIN_DASHBOARD_CROSS_USER_VIEW", "USER", "bob", null);
    }

    @Test
    void viewingAnUnknownTargetUserThrows() {
        DashboardService service = newService();
        User admin = adminUser("root");
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(new SecurityUser(admin), 404L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
