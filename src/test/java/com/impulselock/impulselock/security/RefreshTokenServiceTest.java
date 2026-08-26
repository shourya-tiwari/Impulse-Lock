package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.RefreshToken;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService newService() {
        return new RefreshTokenService(refreshTokenRepository, 7);
    }

    private User user() {
        User user = new User();
        user.setUsername("alice");
        return user;
    }

    @Test
    void issueSavesAHashNotTheRawToken() {
        RefreshTokenService service = newService();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(user());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).hasSize(44); // base64-encoded SHA-256 digest
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void rotateRevokesTheOldTokenAndIssuesANewOne() {
        RefreshTokenService service = newService();
        User user = user();
        RefreshToken existing = new RefreshToken(user, "irrelevant-hash", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<RefreshTokenService.RotatedToken> rotated = service.rotate("some-raw-token");

        assertThat(rotated).isPresent();
        assertThat(rotated.get().user()).isSameAs(user);
        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void rotateIsEmptyForAnUnknownToken() {
        RefreshTokenService service = newService();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.rotate("unknown-token")).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateIsEmptyForARevokedToken() {
        RefreshTokenService service = newService();
        RefreshToken revoked = new RefreshToken(user(), "hash", LocalDateTime.now().plusDays(1));
        revoked.revoke();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThat(service.rotate("some-raw-token")).isEmpty();
    }

    @Test
    void rotateIsEmptyForAnExpiredToken() {
        RefreshTokenService service = newService();
        RefreshToken expired = new RefreshToken(user(), "hash", LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThat(service.rotate("some-raw-token")).isEmpty();
    }

    @Test
    void revokeMarksAKnownTokenRevoked() {
        RefreshTokenService service = newService();
        RefreshToken existing = new RefreshToken(user(), "hash", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        service.revoke("some-raw-token");

        assertThat(existing.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeIsANoOpForAnUnknownToken() {
        RefreshTokenService service = newService();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("unknown-token"); // must not throw
    }

    @Test
    void revokeAllForUserRevokesEveryLiveToken() {
        RefreshTokenService service = newService();
        User user = user();
        RefreshToken first = new RefreshToken(user, "hash-1", LocalDateTime.now().plusDays(1));
        RefreshToken second = new RefreshToken(user, "hash-2", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user)).thenReturn(List.of(first, second));

        service.revokeAllForUser(user);

        assertThat(first.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNotNull();
    }
}
