package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.RefreshToken;
import com.impulselock.impulselock.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Fetches the owning {@code user} alongside the token. {@code RefreshToken.user} is a LAZY
     * {@code @ManyToOne}, and {@code RefreshTokenService.rotate} hands that User straight back to
     * {@code AuthController.refresh}, which uses it after the transaction has closed - to sign the
     * new access token and to build the profile response. Without this graph the caller receives an
     * uninitialized proxy and the first property read off it ({@code getRoles()} in
     * {@code JwtService}) throws {@code LazyInitializationException}, failing every token refresh
     * with a 500. Every lookup of a refresh token needs its user, so fetching them together is
     * both correct and cheaper than the second query a lazy load would have issued anyway.
     *
     * <p>{@code type = LOAD} matters and is not the default. Spring Data's default,
     * {@code EntityGraphType.FETCH}, is a JPA <i>fetch graph</i>: every attribute not named in the
     * graph is treated as LAZY <b>regardless of how it is mapped</b>. Under FETCH this annotation
     * would load the User but silently downgrade its own EAGER {@code roles} and
     * {@code restrictedCategories} to lazy, trading one LazyInitializationException for another a
     * step further along. LOAD is a <i>load graph</i>: fetch these attributes in addition to
     * whatever the entity already declares, leaving the rest of the mapping intact.
     */
    @EntityGraph(attributePaths = "user", type = EntityGraph.EntityGraphType.LOAD)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserAndRevokedAtIsNull(User user);
}
