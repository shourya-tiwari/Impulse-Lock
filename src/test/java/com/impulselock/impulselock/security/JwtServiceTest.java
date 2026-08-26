package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.User;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService newService(long ttlMinutes) {
        return new JwtService("a-test-signing-secret-that-is-long-enough-for-hmac-sha256", ttlMinutes);
    }

    private User userWithRoles(String username, String... roleNames) {
        User user = new User();
        user.setUsername(username);
        Set<Role> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            roles.add(new Role(roleName));
        }
        user.setRoles(roles);
        return user;
    }

    @Test
    void generatedTokenCarriesSubjectAndRoles() {
        JwtService service = newService(15);
        User user = userWithRoles("alice", "ROLE_USER");

        String token = service.generateAccessToken(user);
        Claims claims = service.parseClaims(token).orElseThrow();

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(roles).containsExactly("ROLE_USER");
        assertThat(service.extractUsername(token)).contains("alice");
    }

    @Test
    void aTokenSignedWithADifferentSecretIsRejected() {
        JwtService issuer = newService(15);
        JwtService verifier = new JwtService("a-completely-different-signing-secret-of-sufficient-length", 15);
        String token = issuer.generateAccessToken(userWithRoles("alice", "ROLE_USER"));

        assertThat(verifier.parseClaims(token)).isEmpty();
    }

    @Test
    void aMalformedTokenIsRejected() {
        JwtService service = newService(15);

        assertThat(service.parseClaims("not-a-jwt")).isEmpty();
        assertThat(service.extractUsername("not-a-jwt")).isEmpty();
    }

    @Test
    void anAlreadyExpiredTokenIsRejected() {
        // A negative TTL puts the expiration in the past the instant the token is minted, so
        // this exercises real expiry validation rather than the shape of a non-expired token.
        JwtService service = newService(-1);
        String token = service.generateAccessToken(userWithRoles("alice", "ROLE_USER"));

        assertThat(service.parseClaims(token)).isEmpty();
    }

    @Test
    void accessTokenTtlSecondsMatchesConfiguredMinutes() {
        JwtService service = newService(15);
        assertThat(service.getAccessTokenTtlSeconds()).isEqualTo(15 * 60L);
    }
}
