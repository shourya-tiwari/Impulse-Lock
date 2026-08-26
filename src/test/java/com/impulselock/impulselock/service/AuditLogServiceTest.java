package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.AuditLog;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.AuditLogRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Verifies the guarantee docs/v2/architecture.md#audit-logging (and docs/v2/tasks.md, Phase 4's
 * own test bullet) require: a failure to write an audit row must never fail the calling
 * business operation. Constructed directly (not via a Spring context), so
 * {@code @Transactional(REQUIRES_NEW)} is a no-op here - that's fine, since this test targets
 * {@code record}'s own try/catch behavior, not transaction propagation semantics.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordDoesNotPropagateWhenTheRepositoryThrows() {
        AuditLogService service = new AuditLogService(auditLogRepository, userRepository);
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("database is down"));

        assertThatCode(() -> service.record("TEST_ACTION", "USER", "someone", null)).doesNotThrowAnyException();
    }

    @Test
    void recordSavesWithNullActorWhenThereIsNoAuthenticatedPrincipal() {
        AuditLogService service = new AuditLogService(auditLogRepository, userRepository);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(captor.capture())).thenReturn(null);

        service.record("TEST_ACTION", "USER", "someone", Map.of("key", "value"));

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isNull();
        assertThat(saved.getAction()).isEqualTo("TEST_ACTION");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo("someone");
        assertThat(saved.getMetadata()).containsEntry("key", "value");
    }

    @Test
    void recordResolvesTheActorFromTheSecurityContextWhenPresent() {
        User user = new User();
        user.setUsername("alice");
        SecurityUser securityUser = new SecurityUser(user);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(securityUser, null, java.util.List.of()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        AuditLogService service = new AuditLogService(auditLogRepository, userRepository);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(captor.capture())).thenReturn(null);

        service.record("TEST_ACTION", null, null, null);

        assertThat(captor.getValue().getActor()).isSameAs(user);
    }
}
